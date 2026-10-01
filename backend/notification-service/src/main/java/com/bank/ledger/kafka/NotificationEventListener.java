package com.bank.ledger.kafka;

import com.bank.ledger.event.KafkaTopics;
import com.bank.ledger.service.NotificationConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.bank.ledger.event.EventFields.required;
import static com.bank.ledger.event.EventFields.requiredDecimal;
import static com.bank.ledger.event.EventFields.requiredLong;
import static com.bank.ledger.event.EventFields.requiredText;

/**
 * Consumer Group 3 - Customer alerts.
 * One alert per ledger leg: on an internal transfer the sender gets a DEBIT alert
 * and the receiver gets a CREDIT alert (each to its own customerId).
 * No defaults: a missing field throws -> event goes to the DLT.
 */
@Component
public class NotificationEventListener {

    private final NotificationConsumerService notificationConsumerService;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(NotificationConsumerService notificationConsumerService,
                                     ObjectMapper objectMapper) {
        this.notificationConsumerService = notificationConsumerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.TRANSACTION_EVENTS, groupId = KafkaTopics.NOTIFICATION_GROUP, concurrency = "3")
    public void onNotificationEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode node = objectMapper.readTree(record.value());
        String referenceNo = requiredText(node, "referenceNo");

        JsonNode entries = required(node, "entries");
        if (!entries.isArray() || entries.isEmpty()) {
            throw new IllegalArgumentException("Event " + referenceNo + " has no ledger entries");
        }
        for (JsonNode entry : entries) {
            notificationConsumerService.consumeNotificationEvent(
                    requiredLong(entry, "customerId"),
                    requiredLong(entry, "accountId"),
                    requiredText(entry, "entryType"),
                    requiredDecimal(entry, "amount"),
                    referenceNo);
        }
    }
}
