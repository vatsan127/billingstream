# BillingStream — Implementation Steps Reference

## Step 1 — Enums (`model/`)
**Files:** `PaymentMethod.java`, `TransactionStatus.java`
**Purpose:** Define constants for payment methods (CARD, GPAY) and transaction statuses (SUCCESS, FAILED). Used across models and topology for branching/filtering logic.

## Step 2 — Models (`model/`)
**Files:** `CardTransaction.java`, `GPayTransaction.java`, `UnifiedTransaction.java`
**Purpose:** POJOs representing raw input transactions (Card, GPay) and the canonical unified format. Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`. All status/method fields are `String` since they come from raw JSON.

## Step 3 — Serde (`serde/`)
**Files:** `JsonSerializer.java`, `JsonDeserializer.java`, `JsonSerde.java`
**Purpose:** Generic Jackson-based serialization for Kafka. `JsonSerde<T>` extends `Serdes.WrapperSerde<T>` and wraps `JsonSerializer` + `JsonDeserializer`. Used explicitly in topology via `Consumed.with()`, `Produced.with()`, etc.
**Note:** Uses `tools.jackson.databind.ObjectMapper` (not `com.fasterxml`). No `JavaTimeModule` needed — new Jackson handles it by default. Cannot be set as default serde in YAML since it requires a `Class<T>` constructor arg.

## Step 4 — application.yaml
**File:** `application.yaml`
**Purpose:** Central config — Kafka bootstrap server (`localhost:9092`), Streams application-id (`billingstream`), default key/value serdes (`StringSerde`), server port (`8080`).
**Note:** Default value serde is `StringSerde` because our `JsonSerde` needs a type arg. We override per-stream in topology.

## Step 5 — Config (`config/`)
**Files:** `TopicConfig.java`, `KafkaStreamsConfig.java`
**Purpose:**
- `TopicConfig` — 6 `NewTopic` beans for auto-creating topics on startup. Only creates if topic doesn't exist; won't update existing topics. Use `kafka-topics.sh --alter` to change partitions later.
- `KafkaStreamsConfig` — `@EnableKafkaStreams` to create the `StreamsBuilder` bean + sets `STATESTORE_CACHE_MAX_BYTES_CONFIG=0` for immediate state store visibility during dev.
**Note:** `KafkaProducerConfig` was removed — Spring Boot auto-configures `KafkaTemplate` from `spring-boot-starter-kafka`.

## Step 6 — Topology (`topology/`)
**File:** `TransactionTopology.java`
**Purpose:** All Kafka Streams DSL logic in one class. Uses `@PostConstruct` on injected `StreamsBuilder`. Flow: read card/gpay → mapValues to UnifiedTransaction → merge → split by status (FAILED→DLQ, SUCCESS continues) → write to unified-transactions → branch by payment method (CARD/GPAY topics) → aggregate total amount per method (KTable, state store) → windowed count per minute per method (windowed state store). Uses `split().branch()` instead of `filter()` for single-pass efficiency.

## Step 7 — Simulators (`simulator/`)
**Files:** `CardTransactionSimulator.java`, `GPayTransactionSimulator.java`
**Purpose:** `@Scheduled` producers that generate random transactions every 3 seconds via `KafkaTemplate`. ~20% failure rate. Card simulator produces to `card-transactions`, GPay to `gpay-transactions`. Used for testing the topology without external data.

## Step 8 — Controller (`controller/`)
**File:** `QueryController.java`
**Purpose:** REST endpoints for interactive queries against state stores. Uses `StreamsBuilderFactoryBean` to access `KafkaStreams` runtime instance.
- `GET /api/total-amount/{method}` — queries `total-amount-by-method` KTable store for running total.
- `GET /api/tx-count/{method}` — queries `tx-count-per-minute-by-method` windowed store, fetches last 10 minutes of 1-minute windows.
- Returns 503 if state store not ready (startup/rebalancing).
