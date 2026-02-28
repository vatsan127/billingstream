package com.github.billingstream.simulator;

import com.github.billingstream.model.GPayTransaction;
import com.github.billingstream.model.TransactionStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@Component
@EnableScheduling
public class GPayTransactionSimulator {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Random random = new Random();

    private static final String[] UPI_IDS = {"user1@gpay", "user2@gpay", "user3@gpay", "user4@gpay", "user5@gpay"};
    private static final String[] DEVICES = {"Pixel-8", "Samsung-S24", "iPhone-15", "OnePlus-12"};

    public GPayTransactionSimulator(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    public void simulate() {
        String txId = UUID.randomUUID().toString();
        String status = random.nextDouble() < 0.2
                ? TransactionStatus.FAILED.name()
                : TransactionStatus.SUCCESS.name();

        GPayTransaction tx = GPayTransaction.builder()
                .transactionId(txId)
                .userId("user-" + (1 + random.nextInt(5)))
                .amount(Math.round(random.nextDouble() * 3000 * 100.0) / 100.0)
                .status(status)
                .upiId(UPI_IDS[random.nextInt(UPI_IDS.length)])
                .deviceId(DEVICES[random.nextInt(DEVICES.length)])
                .gpayReferenceId("GPAY-" + UUID.randomUUID().toString().substring(0, 8))
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send("gpay-transactions", txId, tx);
    }
}
