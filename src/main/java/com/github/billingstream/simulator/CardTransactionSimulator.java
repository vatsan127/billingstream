package com.github.billingstream.simulator;

import com.github.billingstream.model.CardTransaction;
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
public class CardTransactionSimulator {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Random random = new Random();

    private static final String[] CARD_NETWORKS = {"VISA", "MASTERCARD", "AMEX", "RUPAY"};
    private static final String[] USERS = {"user-1", "user-2", "user-3", "user-4", "user-5"};

    public CardTransactionSimulator(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    public void simulate() {
        String txId = UUID.randomUUID().toString();
        String status = random.nextDouble() < 0.2
                ? TransactionStatus.FAILED.name()
                : TransactionStatus.SUCCESS.name();

        CardTransaction tx = CardTransaction.builder()
                .transactionId(txId)
                .userId(USERS[random.nextInt(USERS.length)])
                .amount(Math.round(random.nextDouble() * 5000 * 100.0) / 100.0)
                .status(status)
                .cardNumber("XXXX-XXXX-XXXX-" + (1000 + random.nextInt(9000)))
                .cardHolderName("User " + random.nextInt(100))
                .cardNetwork(CARD_NETWORKS[random.nextInt(CARD_NETWORKS.length)])
                .expiryDate("12/28")
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send("card-transactions", txId, tx);
    }
}
