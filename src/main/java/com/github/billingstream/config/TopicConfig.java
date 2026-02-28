package com.github.billingstream.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NewTopic beans only auto-create topics on startup if they don't already exist.
 *
 */
@Configuration
public class TopicConfig {

    @Bean
    public NewTopic cardTransactionsTopic() {
        return new NewTopic("card-transactions", 3, (short) 1);
    }

    @Bean
    public NewTopic gpayTransactionsTopic() {
        return new NewTopic("gpay-transactions", 3, (short) 1);
    }

    @Bean
    public NewTopic failedTransactionsDlqTopic() {
        return new NewTopic("failed-transactions-dlq", 1, (short) 1);
    }

    @Bean
    public NewTopic unifiedTransactionsTopic() {
        return new NewTopic("unified-transactions", 3, (short) 1);
    }

    @Bean
    public NewTopic transactionsCardTopic() {
        return new NewTopic("transactions-card", 3, (short) 1);
    }

    @Bean
    public NewTopic transactionsGpayTopic() {
        return new NewTopic("transactions-gpay", 3, (short) 1);
    }
}
