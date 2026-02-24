package io.loom.core.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DslJsonCodecTest {

    private final DslJsonCodec codec = new DslJsonCodec();

    // ── Simple record round-trip ──────────────────────────────────────

    public record SimpleRecord(String name, int age) {}

    @Test
    void roundTrip_simpleRecord() throws IOException {
        SimpleRecord original = new SimpleRecord("Alice", 30);
        byte[] json = codec.writeValueAsBytes(original);
        SimpleRecord result = codec.readValue(json, SimpleRecord.class);

        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.age()).isEqualTo(30);
    }

    // ── Nested record round-trip ──────────────────────────────────────

    public record Address(String city, String country) {}
    public record Person(String name, Address address) {}

    @Test
    void roundTrip_nestedRecord() throws IOException {
        Person original = new Person("Bob", new Address("London", "UK"));
        byte[] json = codec.writeValueAsBytes(original);
        Person result = codec.readValue(json, Person.class);

        assertThat(result.name()).isEqualTo("Bob");
        assertThat(result.address().city()).isEqualTo("London");
        assertThat(result.address().country()).isEqualTo("UK");
    }

    // ── BigDecimal round-trip ─────────────────────────────────────────

    public record PriceRecord(String item, BigDecimal price) {}

    @Test
    void roundTrip_bigDecimal() throws IOException {
        PriceRecord original = new PriceRecord("Widget", new BigDecimal("19.99"));
        byte[] json = codec.writeValueAsBytes(original);
        PriceRecord result = codec.readValue(json, PriceRecord.class);

        assertThat(result.item()).isEqualTo("Widget");
        assertThat(result.price()).isEqualByComparingTo("19.99");
    }

    // ── OutputStream writing ──────────────────────────────────────────

    @Test
    void writeValue_toOutputStream() throws IOException {
        SimpleRecord original = new SimpleRecord("Charlie", 25);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeValue(out, original);

        SimpleRecord result = codec.readValue(out.toByteArray(), SimpleRecord.class);
        assertThat(result.name()).isEqualTo("Charlie");
        assertThat(result.age()).isEqualTo(25);
    }

    // ── Null fields ───────────────────────────────────────────────────

    public record NullableRecord(String name, String optional) {}

    @Test
    void roundTrip_nullField() throws IOException {
        NullableRecord original = new NullableRecord("Dave", null);
        byte[] json = codec.writeValueAsBytes(original);
        NullableRecord result = codec.readValue(json, NullableRecord.class);

        assertThat(result.name()).isEqualTo("Dave");
        assertThat(result.optional()).isNull();
    }

    @Test
    void nullField_isPresent_inJson() throws IOException {
        NullableRecord original = new NullableRecord("Dave", null);
        byte[] json = codec.writeValueAsBytes(original);
        String jsonStr = new String(json);

        // Verify null fields are written as "field":null, not omitted.
        // This ensures API response shape is identical to Jackson's default.
        assertThat(jsonStr).contains("\"optional\":null");
    }

    // ── Object.class deserialization (passthrough response path) ──────

    @Test
    void readValue_objectClass_jsonObject_returnsMap() throws IOException {
        byte[] json = """
                {"name":"Alice","age":30,"active":true}""".getBytes();
        Object result = codec.readValue(json, Object.class);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("name")).isEqualTo("Alice");
        assertThat(map.get("active")).isEqualTo(true);
    }

    @Test
    void readValue_objectClass_jsonArray_returnsList() throws IOException {
        byte[] json = """
                [1,2,3]""".getBytes();
        Object result = codec.readValue(json, Object.class);

        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertThat(list).containsExactly(1L, 2L, 3L);
    }

    @Test
    void readValue_objectClass_nestedJson_returnsNestedMaps() throws IOException {
        byte[] json = """
                {"user":{"name":"Bob","scores":[10,20]},"total":30}""".getBytes();
        Object result = codec.readValue(json, Object.class);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("user")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) map.get("user");
        assertThat(user.get("name")).isEqualTo("Bob");
        assertThat(user.get("scores")).isInstanceOf(List.class);
    }

    // ── Passthrough round-trip: deserialize as Object, serialize back ─

    @Test
    void passthroughRoundTrip_objectClass_preservesStructure() throws IOException {
        // Simulates: upstream returns JSON → deserialize as Object.class → serialize back
        String originalJson = """
                {"id":42,"name":"Widget","price":19.99,"tags":["sale","new"],"metadata":{"color":"blue"}}""";
        Object deserialized = codec.readValue(originalJson.getBytes(), Object.class);
        byte[] reserialized = codec.writeValueAsBytes(deserialized);
        Object roundTripped = codec.readValue(reserialized, Object.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) roundTripped;
        assertThat(map.get("name")).isEqualTo("Widget");
        assertThat(map.get("tags")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) map.get("tags");
        assertThat(tags).containsExactly("sale", "new");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
        assertThat(metadata.get("color")).isEqualTo("blue");
    }

    // ── Map.class deserialization ─────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void readValue_mapClass_returnsMap() throws IOException {
        byte[] json = """
                {"key":"value","count":5}""".getBytes();
        Map<String, Object> result = codec.readValue(json, Map.class);

        assertThat(result).isNotNull();
        assertThat(result.get("key")).isEqualTo("value");
    }

    // ── LinkedHashMap serialization (passthrough write path) ──────────

    @Test
    void writeValue_linkedHashMap_producesValidJson() throws IOException {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("count", 42);
        map.put("nested", Map.of("key", "val"));

        byte[] json = codec.writeValueAsBytes(map);
        String jsonStr = new String(json);

        assertThat(jsonStr).contains("\"name\":\"test\"");
        assertThat(jsonStr).contains("\"count\":42");

        // Verify it's valid JSON by round-tripping
        Object parsed = codec.readValue(json, Object.class);
        assertThat(parsed).isInstanceOf(Map.class);
    }

    // ── Record with List<Record> field (mirrors real DTOs) ──────────

    public record Review(String author, int rating, String comment) {}
    public record PricingInfo(BigDecimal price, String currency, BigDecimal discount) {}
    public record ProductDetailResponse(
            SimpleRecord product,
            PricingInfo pricing,
            List<Review> reviews
    ) {}

    @Test
    void roundTrip_recordWithListOfRecords() throws IOException {
        ProductDetailResponse original = new ProductDetailResponse(
                new SimpleRecord("Widget", 1),
                new PricingInfo(new BigDecimal("29.99"), "USD", new BigDecimal("5.00")),
                List.of(
                        new Review("Alice", 5, "Great!"),
                        new Review("Bob", 4, "Good")
                )
        );
        byte[] json = codec.writeValueAsBytes(original);
        ProductDetailResponse result = codec.readValue(json, ProductDetailResponse.class);

        assertThat(result.product().name()).isEqualTo("Widget");
        assertThat(result.pricing().price()).isEqualByComparingTo("29.99");
        assertThat(result.pricing().currency()).isEqualTo("USD");
        assertThat(result.reviews()).hasSize(2);
        assertThat(result.reviews().get(0).author()).isEqualTo("Alice");
        assertThat(result.reviews().get(0).rating()).isEqualTo(5);
        assertThat(result.reviews().get(1).author()).isEqualTo("Bob");
    }

    @Test
    void roundTrip_recordWithEmptyList() throws IOException {
        ProductDetailResponse original = new ProductDetailResponse(
                new SimpleRecord("Empty", 0),
                new PricingInfo(BigDecimal.ZERO, "EUR", BigDecimal.ZERO),
                List.of()
        );
        byte[] json = codec.writeValueAsBytes(original);
        ProductDetailResponse result = codec.readValue(json, ProductDetailResponse.class);

        assertThat(result.reviews()).isEmpty();
    }

    @Test
    void roundTrip_recordWithNullList() throws IOException {
        ProductDetailResponse original = new ProductDetailResponse(
                new SimpleRecord("Null", 0),
                new PricingInfo(BigDecimal.ONE, "GBP", null),
                null
        );
        byte[] json = codec.writeValueAsBytes(original);
        ProductDetailResponse result = codec.readValue(json, ProductDetailResponse.class);

        assertThat(result.reviews()).isNull();
        assertThat(result.pricing().discount()).isNull();
    }

    // ── POJO with public fields ─────────────────────────────────────

    public static class PublicFieldPojo {
        public String name;
        public int count;
    }

    @Test
    void roundTrip_pojoWithPublicFields() throws IOException {
        PublicFieldPojo original = new PublicFieldPojo();
        original.name = "test";
        original.count = 42;
        byte[] json = codec.writeValueAsBytes(original);
        PublicFieldPojo result = codec.readValue(json, PublicFieldPojo.class);

        assertThat(result.name).isEqualTo("test");
        assertThat(result.count).isEqualTo(42);
    }

    // ── POJO with private fields + getters/setters (JavaBeans) ────────

    public static class JavaBeanPojo {
        private String name;
        private int age;
        private boolean active;

        public JavaBeanPojo() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    @Test
    void roundTrip_javaBeanPojo() throws IOException {
        JavaBeanPojo original = new JavaBeanPojo();
        original.setName("Alice");
        original.setAge(30);
        original.setActive(true);
        byte[] json = codec.writeValueAsBytes(original);
        JavaBeanPojo result = codec.readValue(json, JavaBeanPojo.class);

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getAge()).isEqualTo(30);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void deserialize_javaBeanPojo_withBooleanIsGetter() throws IOException {
        byte[] json = """
                {"name":"Alice","age":30,"active":true}""".getBytes();
        JavaBeanPojo result = codec.readValue(json, JavaBeanPojo.class);
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getAge()).isEqualTo(30);
        assertThat(result.isActive()).isTrue();
    }

    // ── POJO with all-args constructor + getters (Lombok @Value style) ─

    public static class AllArgsConstructorPojo {
        private final String name;
        private final int age;

        public AllArgsConstructorPojo(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
    }

    @Test
    void roundTrip_allArgsConstructorPojo() throws IOException {
        AllArgsConstructorPojo original = new AllArgsConstructorPojo("Bob", 25);
        byte[] json = codec.writeValueAsBytes(original);
        AllArgsConstructorPojo result = codec.readValue(json, AllArgsConstructorPojo.class);

        assertThat(result.getName()).isEqualTo("Bob");
        assertThat(result.getAge()).isEqualTo(25);
    }

    // ── POJO with nested POJO ─────────────────────────────────────────

    public static class InnerPojo {
        public String city;
        public String country;
    }

    public static class OuterPojo {
        private String name;
        private InnerPojo address;

        public OuterPojo() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public InnerPojo getAddress() { return address; }
        public void setAddress(InnerPojo address) { this.address = address; }
    }

    @Test
    void roundTrip_nestedPojo() throws IOException {
        InnerPojo addr = new InnerPojo();
        addr.city = "London";
        addr.country = "UK";
        OuterPojo original = new OuterPojo();
        original.setName("Charlie");
        original.setAddress(addr);

        byte[] json = codec.writeValueAsBytes(original);
        OuterPojo result = codec.readValue(json, OuterPojo.class);

        assertThat(result.getName()).isEqualTo("Charlie");
        assertThat(result.getAddress().city).isEqualTo("London");
        assertThat(result.getAddress().country).isEqualTo("UK");
    }

    // ── POJO with List<POJO> field ────────────────────────────────────

    public static class ItemPojo {
        public String label;
        public int value;
    }

    public static class ContainerPojo {
        private String title;
        private List<ItemPojo> items;

        public ContainerPojo() {}

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public List<ItemPojo> getItems() { return items; }
        public void setItems(List<ItemPojo> items) { this.items = items; }
    }

    @Test
    void roundTrip_pojoWithListOfPojos() throws IOException {
        ItemPojo item1 = new ItemPojo();
        item1.label = "A";
        item1.value = 1;
        ItemPojo item2 = new ItemPojo();
        item2.label = "B";
        item2.value = 2;

        ContainerPojo original = new ContainerPojo();
        original.setTitle("My List");
        original.setItems(List.of(item1, item2));

        byte[] json = codec.writeValueAsBytes(original);
        ContainerPojo result = codec.readValue(json, ContainerPojo.class);

        assertThat(result.getTitle()).isEqualTo("My List");
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).label).isEqualTo("A");
        assertThat(result.getItems().get(1).value).isEqualTo(2);
    }

    // ── Boolean getter: get-prefix (user chose getXxx for boolean) ─────

    public static class GetPrefixBooleanPojo {
        private String name;
        private boolean active;

        public GetPrefixBooleanPojo() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean getActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    @Test
    void roundTrip_getPrefixBooleanPojo() throws IOException {
        GetPrefixBooleanPojo original = new GetPrefixBooleanPojo();
        original.setName("Alice");
        original.setActive(true);
        byte[] json = codec.writeValueAsBytes(original);
        GetPrefixBooleanPojo result = codec.readValue(json, GetPrefixBooleanPojo.class);

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getActive()).isTrue();
    }

    // ── Boolean getter: mixed is-prefix and get-prefix in same POJO ──

    public static class MixedBooleanPojo {
        private String name;
        private boolean enabled;   // will use isEnabled()
        private boolean visible;   // will use getVisible()

        public MixedBooleanPojo() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean getVisible() { return visible; }
        public void setVisible(boolean visible) { this.visible = visible; }
    }

    @Test
    void roundTrip_mixedBooleanPojo() throws IOException {
        MixedBooleanPojo original = new MixedBooleanPojo();
        original.setName("Test");
        original.setEnabled(true);
        original.setVisible(false);
        byte[] json = codec.writeValueAsBytes(original);
        MixedBooleanPojo result = codec.readValue(json, MixedBooleanPojo.class);

        assertThat(result.getName()).isEqualTo("Test");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getVisible()).isFalse();
    }

    @Test
    void roundTrip_mixedBooleanPojo_bothTrue() throws IOException {
        MixedBooleanPojo original = new MixedBooleanPojo();
        original.setName("Both");
        original.setEnabled(true);
        original.setVisible(true);
        byte[] json = codec.writeValueAsBytes(original);
        MixedBooleanPojo result = codec.readValue(json, MixedBooleanPojo.class);

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getVisible()).isTrue();
    }

    // ── POJO with is-prefix boolean + nested object (exercises custom reader else branch) ──

    public static class NestedAddress {
        public String city;
        public String zip;
    }

    public static class PersonWithFlag {
        private String name;
        private boolean active;   // is-prefix → triggers custom converter
        private NestedAddress address;

        public PersonWithFlag() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public NestedAddress getAddress() { return address; }
        public void setAddress(NestedAddress address) { this.address = address; }
    }

    @Test
    void roundTrip_isPrefix_withNestedObject() throws IOException {
        NestedAddress addr = new NestedAddress();
        addr.city = "NYC";
        addr.zip = "10001";
        PersonWithFlag original = new PersonWithFlag();
        original.setName("Alice");
        original.setActive(true);
        original.setAddress(addr);

        byte[] json = codec.writeValueAsBytes(original);
        PersonWithFlag result = codec.readValue(json, PersonWithFlag.class);

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getAddress()).isNotNull();
        assertThat(result.getAddress().city).isEqualTo("NYC");
        assertThat(result.getAddress().zip).isEqualTo("10001");
    }

    @Test
    void roundTrip_isPrefix_withNullNestedObject() throws IOException {
        PersonWithFlag original = new PersonWithFlag();
        original.setName("Bob");
        original.setActive(false);
        original.setAddress(null);

        byte[] json = codec.writeValueAsBytes(original);
        PersonWithFlag result = codec.readValue(json, PersonWithFlag.class);

        assertThat(result.getName()).isEqualTo("Bob");
        assertThat(result.isActive()).isFalse();
        assertThat(result.getAddress()).isNull();
    }

    // ── Record containing a POJO with is-prefix boolean (nested type registration) ──

    public static class StatusPojo {
        private boolean active;

        public StatusPojo() {}

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public record UserWithStatus(String name, StatusPojo status) {}

    @Test
    void roundTrip_recordContainingIsPrefixPojo() throws IOException {
        StatusPojo status = new StatusPojo();
        status.setActive(true);
        UserWithStatus original = new UserWithStatus("Alice", status);

        byte[] json = codec.writeValueAsBytes(original);
        UserWithStatus result = codec.readValue(json, UserWithStatus.class);

        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.status()).isNotNull();
        assertThat(result.status().isActive()).isTrue();
    }

    // ── Concurrent writer pool reuse (virtual threads) ────────────────

    @Test
    void concurrentAccess_writerPoolIsThreadSafe() throws Exception {
        int threadCount = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        // Each thread does a write + read cycle
                        SimpleRecord rec = new SimpleRecord("Thread-" + idx, idx);
                        byte[] json = codec.writeValueAsBytes(rec);
                        SimpleRecord result = codec.readValue(json, SimpleRecord.class);
                        assertThat(result.name()).isEqualTo("Thread-" + idx);
                        assertThat(result.age()).isEqualTo(idx);
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // release all threads at once
            doneLatch.await();
        }

        assertThat(errors).isEmpty();
    }

    // ── Concurrent access to is-prefix POJO (race condition regression) ──

    @Test
    void concurrentAccess_isPrefixPojo_threadSafe() throws Exception {
        // Use a FRESH codec so the type hasn't been pre-registered.
        // All 200 threads hit ensureBooleanGetterSupport() simultaneously.
        DslJsonCodec freshCodec = new DslJsonCodec();
        int threadCount = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        JavaBeanPojo pojo = new JavaBeanPojo();
                        pojo.setName("Thread-" + idx);
                        pojo.setAge(idx);
                        pojo.setActive(idx % 2 == 0);
                        byte[] json = freshCodec.writeValueAsBytes(pojo);
                        JavaBeanPojo result = freshCodec.readValue(json, JavaBeanPojo.class);
                        assertThat(result.getName()).isEqualTo("Thread-" + idx);
                        assertThat(result.getAge()).isEqualTo(idx);
                        assertThat(result.isActive()).isEqualTo(idx % 2 == 0);
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
        }

        assertThat(errors).isEmpty();
    }
}
