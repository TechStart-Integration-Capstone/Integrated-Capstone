package com.bank.ledger.kafka;

import com.bank.ledger.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * ledger-core owns the topics it produces to. Spring Kafka's KafkaAdmin creates them at startup
 * (6 partitions) instead of relying on broker auto-create (which would give 1 partition).
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TRANSACTION_EVENTS)
                .partitions(KafkaTopics.PARTITIONS)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionEventsDltTopic() {
        // Same partition count: DeadLetterPublishingRecoverer writes to the same partition number by default
        return TopicBuilder.name(KafkaTopics.TRANSACTION_EVENTS_DLT)
                .partitions(KafkaTopics.PARTITIONS)
                .replicas(1)
                .build();
    }
}
