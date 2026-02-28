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
**Files:** `TopicConfig.java`, `KafkaProducerConfig.java`, `KafkaStreamsConfig.java`
**Purpose:**
- `TopicConfig` — 6 `NewTopic` beans for auto-creating topics on startup. Only creates if topic doesn't exist; won't update existing topics. Use `kafka-topics.sh --alter` to change partitions later.
- `KafkaProducerConfig` — `KafkaTemplate<String, Object>` bean used by simulators to produce test data.
- `KafkaStreamsConfig` — `@EnableKafkaStreams` to create the `StreamsBuilder` bean + sets `STATESTORE_CACHE_MAX_BYTES_CONFIG=0` for immediate state store visibility during dev.

## Step 6 — Topology (`topology/`)
**File:** `TransactionTopology.java`
**Purpose:** (pending)

## Step 7 — Simulators (`simulator/`)
**Files:** `CardTransactionSimulator.java`, `GPayTransactionSimulator.java`
**Purpose:** (pending)

## Step 8 — Controller (`controller/`)
**File:** `QueryController.java`
**Purpose:** (pending)
