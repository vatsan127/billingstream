package com.github.billingstream.topology;

import com.github.billingstream.model.CardTransaction;
import com.github.billingstream.model.GPayTransaction;
import com.github.billingstream.model.PaymentMethod;
import com.github.billingstream.model.TransactionStatus;
import com.github.billingstream.model.UnifiedTransaction;
import com.github.billingstream.serde.JsonSerde;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TransactionTopology {

    private final StreamsBuilder streamsBuilder;

    public TransactionTopology(StreamsBuilder streamsBuilder) {
        this.streamsBuilder = streamsBuilder;
    }

    @PostConstruct
    public void buildTopology() {
        JsonSerde<CardTransaction> cardSerde = new JsonSerde<>(CardTransaction.class);
        JsonSerde<GPayTransaction> gpaySerde = new JsonSerde<>(GPayTransaction.class);
        JsonSerde<UnifiedTransaction> unifiedSerde = new JsonSerde<>(UnifiedTransaction.class);

        // 1. Read input streams
        KStream<String, CardTransaction> cardStream = streamsBuilder.stream(
                "card-transactions", Consumed.with(Serdes.String(), cardSerde));

        KStream<String, GPayTransaction> gpayStream = streamsBuilder.stream(
                "gpay-transactions", Consumed.with(Serdes.String(), gpaySerde));

        // 2. Map each to UnifiedTransaction (before merge, since types differ)
        KStream<String, UnifiedTransaction> unifiedCardStream = cardStream.mapValues(card ->
                UnifiedTransaction.builder()
                        .transactionId(card.getTransactionId())
                        .userId(card.getUserId())
                        .amount(card.getAmount())
                        .status(card.getStatus())
                        .paymentMethod(PaymentMethod.CARD.name())
                        .paymentReference(card.getCardNumber())
                        .paymentDetail(card.getCardNetwork())
                        .timestamp(card.getTimestamp())
                        .build());

        KStream<String, UnifiedTransaction> unifiedGpayStream = gpayStream.mapValues(gpay ->
                UnifiedTransaction.builder()
                        .transactionId(gpay.getTransactionId())
                        .userId(gpay.getUserId())
                        .amount(gpay.getAmount())
                        .status(gpay.getStatus())
                        .paymentMethod(PaymentMethod.GPAY.name())
                        .paymentReference(gpay.getUpiId())
                        .paymentDetail(gpay.getGpayReferenceId())
                        .timestamp(gpay.getTimestamp())
                        .build());

        // 3. Merge both unified streams
        KStream<String, UnifiedTransaction> mergedStream = unifiedCardStream.merge(unifiedGpayStream);

        // 4. Split: FAILED → DLQ, SUCCESS continues

        // filter iterates the stream twice:
        //   mergedStream.filter((key, tx) -> "FAILED".equals(tx.getStatus()))
        //       .to("failed-transactions-dlq", Produced.with(Serdes.String(), unifiedSerde));
        //   KStream<String, UnifiedTransaction> successStream = mergedStream
        //       .filter((key, tx) -> "SUCCESS".equals(tx.getStatus()));

        //  Alternative: use split() instead of filter(), split() processes each record once and routes it — more efficient.
        var branches = mergedStream.split(Named.as("status-"))
                .branch((key, tx) -> TransactionStatus.FAILED.name().equals(tx.getStatus()), Branched.as("failed"))
                .defaultBranch(Branched.as("success"));

        KStream<String, UnifiedTransaction> failedStream = branches.get("status-failed");
        KStream<String, UnifiedTransaction> successStream = branches.get("status-success");

        // Send failed transactions to DLQ and success transactions to unified-transactions topic
        failedStream.to("failed-transactions-dlq", Produced.with(Serdes.String(), unifiedSerde));
        successStream.to("unified-transactions", Produced.with(Serdes.String(), unifiedSerde));

        // 5. Branch success by payment method → card/gpay topics
        var methodBranches = successStream.split(Named.as("method-"))
                .branch((key, tx) -> PaymentMethod.CARD.name().equals(tx.getPaymentMethod()), Branched.as("card"))
                .branch((key, tx) -> PaymentMethod.GPAY.name().equals(tx.getPaymentMethod()), Branched.as("gpay"))
                .noDefaultBranch();

        methodBranches.get("method-card").to("transactions-card", Produced.with(Serdes.String(), unifiedSerde));
        methodBranches.get("method-gpay").to("transactions-gpay", Produced.with(Serdes.String(), unifiedSerde));

        // 6. Aggregate total amount per payment method
        successStream
                .selectKey((key, tx) -> tx.getPaymentMethod())
                .groupByKey(Grouped.with(Serdes.String(), unifiedSerde))
                .aggregate(
                        () -> 0.0,
                        (method, tx, total) -> total + tx.getAmount(),
                        Materialized.<String, Double, KeyValueStore<Bytes, byte[]>>as("total-amount-by-method")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Double()));

        // 7. Windowed count per minute per payment method
        successStream
                .selectKey((key, tx) -> tx.getPaymentMethod())
                .groupByKey(Grouped.with(Serdes.String(), unifiedSerde))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("tx-count-per-minute-by-method")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()));
    }
}
