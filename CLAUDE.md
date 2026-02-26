# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./mvnw clean compile              # Compile
./mvnw spring-boot:run            # Run (requires Kafka on localhost:9092)
./mvnw test                       # Run all tests
./mvnw test -Dtest=ClassName      # Run a single test class
./mvnw test -Dtest=ClassName#method  # Run a single test method
```

## Project Overview

BillingStream is a Kafka Streams + Spring Boot application that processes Card and GPay billing transactions in real-time. It filters, transforms, branches, aggregates, and windows payment data using the Kafka Streams DSL.

**Stack:** Spring Boot 4.0.3, Java 21, Kafka Streams, Jackson JSON SerDe, Lombok, Maven

## Architecture

Single Spring Boot app under `com.github.billingstream`, organized by feature:

- **config/** — Kafka Streams config (`StreamsBuilder` bean), `KafkaTemplate` for simulators, `NewTopic` beans for auto-creating topics
- **model/** — `CardTransaction`, `GPayTransaction` (inputs), `UnifiedTransaction` (canonical form), enums (`PaymentMethod`, `TransactionStatus`)
- **serde/** — Generic Jackson-based `JsonSerde<T>` (serializer + deserializer wrapper)
- **topology/** — `TransactionTopology` — all Kafka Streams DSL logic in one class
- **simulator/** — `CardTransactionSimulator`, `GPayTransactionSimulator` — produce fake data using `KafkaTemplate`
- **controller/** — `QueryController` — REST endpoints for interactive queries against state stores

## Topology Flow

Card and GPay input streams merge → failed transactions filter to `failed-transactions-dlq` → successful ones transform to `UnifiedTransaction` → branch by payment method to `transactions-card`/`transactions-gpay` → aggregate total amount per method (state store) → windowed count per minute per method (state store).

## Key Constraints

- **JSON only** — Jackson SerDe everywhere. No Avro, no Schema Registry.
- **Pure Kafka Streams DSL** — No `@KafkaListener` or `KafkaTemplate` in topologies. `KafkaTemplate` is only for simulators.
- **Materialized state stores** — Every stateful operation uses `Materialized.as("store-name")` for interactive queries.
- **Single app** — No microservices. Everything in one deployable unit, split by packages.

## Kafka Topics

| Topic | Purpose |
|---|---|
| `card-transactions` | Input — raw Card transactions |
| `gpay-transactions` | Input — raw GPay transactions |
| `failed-transactions-dlq` | DLQ — failed transactions |
| `unified-transactions` | Transformed unified format |
| `transactions-card` | Branch — Card only |
| `transactions-gpay` | Branch — GPay only |

**State stores:** `total-amount-by-method`, `tx-count-per-minute-by-method`

## REST Endpoints

- `GET /api/total-amount/{method}` — aggregated total by CARD or GPAY
- `GET /api/tx-count/{method}` — windowed count per minute by method
