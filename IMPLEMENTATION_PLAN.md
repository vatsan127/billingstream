# BillingStream — Full Implementation Plan

## Context

The project is scaffolded (Spring Boot 4.0.3, Java 21, Maven) but has zero application code — only `BillingstreamApplication.java` and a minimal `application.yaml`. The goal is to implement the entire Card/GPay billing transaction pipeline using Kafka Streams DSL, end-to-end, in a single pass.

## Implementation Steps

Steps are ordered so each one compiles independently. **14 new files** total.

---

### Step 1 — Enums (`model/`)
Create `PaymentMethod.java` (CARD, GPAY) and `TransactionStatus.java` (SUCCESS, FAILED). Plain enums, no dependencies.

### Step 2 — Models (`model/`)
Create three Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` POJOs:
- `CardTransaction` — transactionId, userId, amount, status, cardNumber, cardHolderName, cardNetwork, expiryDate, timestamp (Instant)
- `GPayTransaction` — transactionId, userId, amount, status, upiId, deviceId, gpayReferenceId, timestamp (Instant)
- `UnifiedTransaction` — transactionId, userId, amount, status, paymentMethod, paymentReference, paymentDetail, timestamp (Instant)

All `status`/`paymentMethod` fields are `String` (not enum) since they come from raw JSON.

### Step 3 — Serde (`serde/`)
Three generic Jackson-based classes:
- `JsonSerializer<T>` — implements `Serializer<T>`, registers `JavaTimeModule`, disables `WRITE_DATES_AS_TIMESTAMPS`
- `JsonDeserializer<T>` — implements `Deserializer<T>`, takes `Class<T>` in constructor
- `JsonSerde<T>` — implements `Serde<T>`, wraps serializer + deserializer

### Step 4 — application.yaml (update)
Replace minimal yaml with full config:
- `spring.kafka.bootstrap-servers: localhost:9092`
- `spring.kafka.streams.application-id: billingstream-app`
- Default key/value serde = `StringSerde`
- Producer: `StringSerializer` key, Spring `JsonSerializer` value, `spring.json.add.type.headers: false`
- Custom `billingstream.simulator.*` properties (interval-ms, failure-rate for card and gpay)
- Custom `billingstream.topics.*` for topic names
- Actuator endpoints (health, info, metrics)

### Step 5 — Config (`config/`)
- `TopicConfig.java` — 6 `NewTopic` beans (card-transactions, gpay-transactions, failed-transactions-dlq, unified-transactions, transactions-card, transactions-gpay), 3 partitions each (1 for DLQ)
- `KafkaProducerConfig.java` — `KafkaTemplate<String, Object>` bean using `KafkaProperties`
- `KafkaStreamsConfig.java` — `@EnableKafkaStreams`, `KafkaStreamsConfiguration` bean with application-id, bootstrap-servers, default serdes, `STATESTORE_CACHE_MAX_BYTES_CONFIG=0` (dev mode: immediate visibility in state stores)

### Step 6 — Topology (`topology/TransactionTopology.java`)
`@Component` with `@PostConstruct` that builds the full DSL topology on the injected `StreamsBuilder`:

1. **Read** card-transactions and gpay-transactions with explicit typed SerDes
2. **mapValues** each to `UnifiedTransaction` (transform before merge — required because types differ)
3. **merge** both unified streams
4. **split/branch** by status: FAILED → `failed-transactions-dlq`, SUCCESS continues
5. **to** `unified-transactions` (success stream)
6. **split/branch** success by paymentMethod → `transactions-card` / `transactions-gpay`
7. **selectKey + groupByKey + aggregate** total amount per method → state store `total-amount-by-method` (Double accumulator)
8. **selectKey + groupByKey + windowedBy(1 min tumbling) + count** → state store `tx-count-per-minute-by-method`

Uses modern `split().branch().defaultBranch()` API (not deprecated array overload). Every stateful op uses `Materialized.as()`.

### Step 7 — Simulators (`simulator/`)
- `CardTransactionSimulator.java` — `@Component @EnableScheduling`, `@Scheduled(fixedDelayString)` producing random CardTransactions via `KafkaTemplate`. Configurable interval and failure rate from yaml.
- `GPayTransactionSimulator.java` — same pattern for GPay transactions with UPI IDs.

### Step 8 — Controller (`controller/QueryController.java`)
`@RestController @RequestMapping("/api")`:
- `GET /api/total-amount/{method}` — queries `ReadOnlyKeyValueStore<String, Double>` from `total-amount-by-method`
- `GET /api/tx-count/{method}` — queries `ReadOnlyWindowStore<String, Long>` from `tx-count-per-minute-by-method`, fetches last 10 minutes of windows
- Both catch `InvalidStateStoreException` → return 503

---

## Files Created/Modified

| File | Action |
|---|---|
| `src/main/java/.../model/PaymentMethod.java` | Create |
| `src/main/java/.../model/TransactionStatus.java` | Create |
| `src/main/java/.../model/CardTransaction.java` | Create |
| `src/main/java/.../model/GPayTransaction.java` | Create |
| `src/main/java/.../model/UnifiedTransaction.java` | Create |
| `src/main/java/.../serde/JsonSerializer.java` | Create |
| `src/main/java/.../serde/JsonDeserializer.java` | Create |
| `src/main/java/.../serde/JsonSerde.java` | Create |
| `src/main/resources/application.yaml` | Update |
| `src/main/java/.../config/TopicConfig.java` | Create |
| `src/main/java/.../config/KafkaProducerConfig.java` | Create |
| `src/main/java/.../config/KafkaStreamsConfig.java` | Create |
| `src/main/java/.../topology/TransactionTopology.java` | Create |
| `src/main/java/.../simulator/CardTransactionSimulator.java` | Create |
| `src/main/java/.../simulator/GPayTransactionSimulator.java` | Create |
| `src/main/java/.../controller/QueryController.java` | Create |

(`...` = `com/github/billingstream`)

## Verification

```bash
# Compile check after each step
./mvnw clean compile

# Full run (requires Kafka on localhost:9092)
./mvnw spring-boot:run

# Test endpoints after ~5 seconds
curl http://localhost:8080/api/total-amount/CARD
curl http://localhost:8080/api/total-amount/GPAY
curl http://localhost:8080/api/tx-count/CARD
curl http://localhost:8080/api/tx-count/GPAY
```
