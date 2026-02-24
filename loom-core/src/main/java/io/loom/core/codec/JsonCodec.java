package io.loom.core.codec;

import java.io.IOException;
import java.io.OutputStream;

public interface JsonCodec {

    <T> T readValue(byte[] json, Class<T> type) throws IOException;

    byte[] writeValueAsBytes(Object value) throws IOException;

    void writeValue(OutputStream out, Object value) throws IOException;
}
