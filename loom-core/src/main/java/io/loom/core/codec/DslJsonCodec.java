package io.loom.core.codec;

import com.dslplatform.json.*;
import com.dslplatform.json.runtime.Settings;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class DslJsonCodec implements JsonCodec {

    private static final System.Logger LOG = System.getLogger(DslJsonCodec.class.getName());
    private static final int MAX_POOL_SIZE = 256;

    private final DslJson<Object> dslJson;
    private final LinkedBlockingQueue<JsonWriter> writerPool = new LinkedBlockingQueue<>(MAX_POOL_SIZE);
    private final ConcurrentHashMap<Class<?>, Boolean> checkedTypes = new ConcurrentHashMap<>();
    private final HashSet<Class<?>> inProgress = new HashSet<>(); // only accessed under analysisLock
    private final ReentrantLock analysisLock = new ReentrantLock();

    public DslJsonCodec() {
        this.dslJson = new DslJson<>(Settings.withRuntime()
                .allowArrayFormat(true)
                .includeServiceLoader());
    }

    @Override
    public <T> T readValue(byte[] json, Class<T> type) throws IOException {
        ensureBooleanGetterSupport(type);
        return dslJson.deserialize(type, json, json.length);
    }

    @Override
    public byte[] writeValueAsBytes(Object value) throws IOException {
        if (value != null) ensureBooleanGetterSupport(value.getClass());
        JsonWriter writer = borrowWriter();
        try {
            dslJson.serialize(writer, value);
            return writer.toByteArray();
        } finally {
            returnWriter(writer);
        }
    }

    @Override
    public void writeValue(OutputStream out, Object value) throws IOException {
        if (value != null) ensureBooleanGetterSupport(value.getClass());
        JsonWriter writer = borrowWriter();
        try {
            dslJson.serialize(writer, value);
            writer.toStream(out);
        } finally {
            returnWriter(writer);
        }
    }

    // ── Boolean is-prefix getter support ─────────────────────────────────
    //
    // dsl-json's ObjectAnalyzer only recognizes getXxx() methods, not the
    // JavaBeans isXxx() convention for boolean fields. We detect affected
    // types — including nested field types inside records and POJOs — and
    // register correct converters BEFORE ObjectAnalyzer runs.
    //
    // Uses ReentrantLock (not synchronized) to avoid carrier thread pinning
    // on virtual threads, and to allow safe recursive scanning of nested types.

    private void ensureBooleanGetterSupport(Class<?> type) {
        if (skipType(type)) return;
        if (checkedTypes.containsKey(type)) return; // fast path — zero contention after first call
        analyzeType(type);
    }

    private void analyzeType(Class<?> type) {
        analysisLock.lock();
        try {
            if (checkedTypes.containsKey(type)) return; // already fully registered
            if (!inProgress.add(type)) return;           // circular reference guard

            if (type.isRecord()) {
                // Records are handled natively by dsl-json, but we must scan their
                // component types to pre-register nested POJOs with is-prefix booleans.
                for (RecordComponent component : type.getRecordComponents()) {
                    ensureBooleanGetterSupport(component.getType());
                    scanGenericTypeArgs(component.getGenericType());
                }
            } else {
                // POJO — check for is-prefix boolean getters and scan nested field types
                BeanInfo beanInfo = Introspector.getBeanInfo(type, Object.class);
                PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();

                // Recursively pre-register nested field types
                for (PropertyDescriptor pd : descriptors) {
                    ensureBooleanGetterSupport(pd.getPropertyType());
                    Method getter = pd.getReadMethod();
                    if (getter != null) {
                        scanGenericTypeArgs(getter.getGenericReturnType());
                    }
                }

                // Check if this type itself needs a custom converter
                boolean hasIsGetter = false;
                for (PropertyDescriptor pd : descriptors) {
                    Method getter = pd.getReadMethod();
                    if (getter != null && getter.getName().startsWith("is") &&
                            (getter.getReturnType() == boolean.class || getter.getReturnType() == Boolean.class)) {
                        hasIsGetter = true;
                        break;
                    }
                }
                if (hasIsGetter) {
                    registerCustomConverters(type, descriptors);
                }
            }

            // Only mark as done AFTER registration completes — other threads
            // block on analysisLock until converters are fully registered.
            checkedTypes.put(type, Boolean.TRUE);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "[Loom] Failed to analyze type " + type.getName()
                    + " for boolean getter support: " + e.getMessage());
            // Fall through to dsl-json default; mark done to avoid retrying
            checkedTypes.put(type, Boolean.TRUE);
        } finally {
            inProgress.remove(type);
            analysisLock.unlock();
        }
    }

    private void scanGenericTypeArgs(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            for (Type typeArg : pt.getActualTypeArguments()) {
                if (typeArg instanceof Class<?> clazz) {
                    ensureBooleanGetterSupport(clazz);
                } else if (typeArg instanceof ParameterizedType nestedPt) {
                    if (nestedPt.getRawType() instanceof Class<?> rawClass) {
                        ensureBooleanGetterSupport(rawClass);
                    }
                    scanGenericTypeArgs(nestedPt);
                }
            }
        }
    }

    private static boolean skipType(Class<?> type) {
        return type.isPrimitive() || type == Object.class ||
                type == String.class || type == Boolean.class || type == Character.class ||
                Number.class.isAssignableFrom(type) ||
                Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type) ||
                type.isArray() || type.isEnum();
    }

    // ── Custom converter registration ────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void registerCustomConverters(Class<T> type, PropertyDescriptor[] descriptors) throws Exception {
        List<PropAccessor> props = new ArrayList<>();
        for (PropertyDescriptor pd : descriptors) {
            Method getter = pd.getReadMethod();
            Method setter = pd.getWriteMethod();
            if (getter == null && setter == null) continue;
            if (getter != null) getter.setAccessible(true);
            if (setter != null) setter.setAccessible(true);
            props.add(new PropAccessor(pd.getName(), pd.getPropertyType(), getter, setter));
        }

        // Register writer
        dslJson.registerWriter((Class) type, (JsonWriter.WriteObject<Object>) (writer, value) -> {
            if (value == null) {
                writer.writeNull();
                return;
            }
            writer.writeByte(JsonWriter.OBJECT_START);
            boolean first = true;
            for (PropAccessor prop : props) {
                if (prop.getter == null) continue;
                try {
                    if (!first) writer.writeByte(JsonWriter.COMMA);
                    first = false;
                    writer.writeString(prop.name);
                    writer.writeByte(JsonWriter.SEMI);
                    Object val = prop.getter.invoke(value);
                    writePropertyValue(writer, val, prop.type);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize property: " + prop.name, e);
                }
            }
            writer.writeByte(JsonWriter.OBJECT_END);
        });

        // Register reader
        Constructor<T> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Map<String, PropAccessor> propMap = new LinkedHashMap<>();
        for (PropAccessor prop : props) {
            propMap.put(prop.name, prop);
        }

        dslJson.registerReader((Class) type, (JsonReader.ReadObject<Object>) reader -> {
            if (reader.wasNull()) return null;
            try {
                T instance = ctor.newInstance();
                if (reader.last() != '{') {
                    throw new IOException("Expecting '{' for " + type.getName());
                }
                if (reader.getNextToken() == '}') return instance;

                String key = reader.readKey();
                readAndSet(reader, instance, propMap.get(key));

                while (reader.getNextToken() == ',') {
                    reader.getNextToken();
                    key = reader.readKey();
                    readAndSet(reader, instance, propMap.get(key));
                }

                return instance;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Failed to deserialize " + type.getName(), e);
            }
        });
    }

    private void writePropertyValue(JsonWriter writer, Object val, Class<?> type) throws IOException {
        if (val == null) {
            writer.writeNull();
        } else if (type == boolean.class || type == Boolean.class) {
            BoolConverter.serialize((Boolean) val, writer);
        } else if (type == int.class || type == Integer.class) {
            NumberConverter.serialize((Integer) val, writer);
        } else if (type == long.class || type == Long.class) {
            NumberConverter.serialize((Long) val, writer);
        } else if (type == double.class || type == Double.class) {
            NumberConverter.serialize((Double) val, writer);
        } else if (type == float.class || type == Float.class) {
            NumberConverter.serialize((Float) val, writer);
        } else if (type == String.class) {
            StringConverter.serialize((String) val, writer);
        } else if (type == BigDecimal.class) {
            NumberConverter.serialize((BigDecimal) val, writer);
        } else {
            dslJson.serialize(writer, val.getClass(), val);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void readAndSet(JsonReader<?> reader, T instance, PropAccessor prop) throws Exception {
        if (prop == null || prop.setter == null) {
            reader.skip();
            return;
        }
        Class<?> type = prop.type;
        Object val;
        if (reader.wasNull()) {
            val = null;
        } else if (type == boolean.class || type == Boolean.class) {
            val = BoolConverter.deserialize(reader);
        } else if (type == int.class || type == Integer.class) {
            val = NumberConverter.deserializeInt(reader);
        } else if (type == long.class || type == Long.class) {
            val = NumberConverter.deserializeLong(reader);
        } else if (type == double.class || type == Double.class) {
            val = NumberConverter.deserializeDouble(reader);
        } else if (type == float.class || type == Float.class) {
            val = NumberConverter.deserializeFloat(reader);
        } else if (type == String.class) {
            val = StringConverter.deserialize(reader);
        } else if (type == BigDecimal.class) {
            val = NumberConverter.deserializeDecimal(reader);
        } else {
            // Nested object/array — reader.last() is already at value start
            // (readKey() positions reader at first byte of value after ':')
            JsonReader.ReadObject<?> typeReader = dslJson.tryFindReader(type);
            if (typeReader != null) {
                val = typeReader.read(reader);
            } else {
                reader.skip();
                return;
            }
        }
        prop.setter.invoke(instance, val);
    }

    // ── Writer pool (bounded) ────────────────────────────────────────────

    private JsonWriter borrowWriter() {
        JsonWriter writer = writerPool.poll();
        if (writer != null) {
            writer.reset();
            return writer;
        }
        return dslJson.newWriter();
    }

    private void returnWriter(JsonWriter writer) {
        writer.reset();
        writerPool.offer(writer); // silently drops if full
    }

    // ── Property accessor record ─────────────────────────────────────────

    private record PropAccessor(String name, Class<?> type, Method getter, Method setter) {}
}
