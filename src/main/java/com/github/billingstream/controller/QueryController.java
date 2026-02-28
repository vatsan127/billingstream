package com.github.billingstream.controller;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final StreamsBuilderFactoryBean factoryBean;

    public QueryController(StreamsBuilderFactoryBean factoryBean) {
        this.factoryBean = factoryBean;
    }

    // GET /api/total-amount/CARD or /api/total-amount/GPAY
    @GetMapping("/total-amount/{method}")
    public ResponseEntity<Map<String, Object>> getTotalAmount(@PathVariable String method) {
        try {
            KafkaStreams kafkaStreams = factoryBean.getKafkaStreams();
            ReadOnlyKeyValueStore<String, Double> store = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType("total-amount-by-method", QueryableStoreTypes.keyValueStore()));

            Double total = store.get(method.toUpperCase());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("method", method.toUpperCase());
            response.put("totalAmount", total != null ? total : 0.0);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", "State store not ready: " + e.getMessage()));
        }
    }

    // GET /api/tx-count/CARD or /api/tx-count/GPAY
    @GetMapping("/tx-count/{method}")
    public ResponseEntity<Map<String, Object>> getTransactionCount(@PathVariable String method) {
        try {
            KafkaStreams kafkaStreams = factoryBean.getKafkaStreams();
            ReadOnlyWindowStore<String, Long> store = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType("tx-count-per-minute-by-method", QueryableStoreTypes.windowStore()));

            Instant now = Instant.now();
            Instant from = now.minus(Duration.ofMinutes(10));

            Map<String, Long> windows = new LinkedHashMap<>();
            try (WindowStoreIterator<Long> iterator = store.fetch(method.toUpperCase(), from, now)) {
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    String windowStart = Instant.ofEpochMilli(entry.key).toString();
                    windows.put(windowStart, entry.value);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("method", method.toUpperCase());
            response.put("windows", windows);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", "State store not ready: " + e.getMessage()));
        }
    }
}
