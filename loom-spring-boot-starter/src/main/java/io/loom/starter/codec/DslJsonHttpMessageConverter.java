package io.loom.starter.codec;

import io.loom.core.codec.JsonCodec;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.util.List;

public class DslJsonHttpMessageConverter implements HttpMessageConverter<Object> {

    private static final List<MediaType> SUPPORTED = List.of(
            MediaType.APPLICATION_JSON,
            new MediaType("application", "*+json"));

    private final JsonCodec jsonCodec;

    public DslJsonHttpMessageConverter(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return mediaType == null || SUPPORTED.stream().anyMatch(s -> s.includes(mediaType));
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return mediaType == null || SUPPORTED.stream().anyMatch(s -> s.includes(mediaType));
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return SUPPORTED;
    }

    @Override
    public Object read(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try {
            byte[] bytes = inputMessage.getBody().readAllBytes();
            return jsonCodec.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new HttpMessageNotReadableException("Failed to read JSON", e, inputMessage);
        }
    }

    @Override
    public void write(Object o, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        outputMessage.getHeaders().setContentType(
                contentType != null && !MediaType.ALL.equals(contentType) ? contentType : MediaType.APPLICATION_JSON);
        jsonCodec.writeValue(outputMessage.getBody(), o);
    }
}
