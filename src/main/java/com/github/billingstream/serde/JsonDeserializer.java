package com.github.billingstream.serde;

import org.apache.kafka.common.serialization.Deserializer;
import tools.jackson.databind.ObjectMapper;

public class JsonDeserializer<T> implements Deserializer<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Class<T> type;

    public JsonDeserializer(Class<T> type) {
        this.type = type;
    }

    @Override
    public T deserialize(String topic, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return MAPPER.readValue(bytes, type);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing " + type.getSimpleName(), e);
        }
    }
}
