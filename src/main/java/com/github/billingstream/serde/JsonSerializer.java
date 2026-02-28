package com.github.billingstream.serde;

import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.ObjectMapper;

public class JsonSerializer<T> implements Serializer<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) return null;
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing to JSON", e);
        }
    }
}
