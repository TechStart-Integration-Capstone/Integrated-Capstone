package com.bank.ledger.kafka;

import com.bank.ledger.event.KafkaTopics;
import com.bank.ledger.service.AuditConsumerService;
import com.bank.ledger.service.ReconciliationService;
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
 * Consumer groups owned by event-consumers.
 * Exceptions propagate to KafkaErrorHandlingConfig: transient -> retry 3x -> DLT; malformed -> DLT immediately.
 */
@Component
public class AuditAndReconEventListeners {

    private final AuditConsumerService auditConsumerService;
    private final ReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    public AuditAndReconEventListeners(AuditConsumerService auditConsumerService,
                                       ReconciliationService reconciliationService,
                                       ObjectMapper objectMapper) {
        this.auditConsumerService = auditConsumerService;
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumer Group 1 - Immutable PostgreSQL financial audit (one row per ledger leg).
     */
    @KafkaListener(topics = KafkaTopics.TRANSACTION_EVENTS, groupId = KafkaTopics.AUDIT_GROUP, concurrency = "3")
    public void onAuditTransactionEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode node = objectMapper.readTree(record.value());
        Long txId = requiredLong(node, "transactionId");

        JsonNode entries = required(node, "entries");
        if (!entries.isArray() || entries.isEmpty()) {
            throw new IllegalArgumentException("Event " + txId + " has no ledger entries");
        }
        for (JsonNode entry : entries) {
            auditConsumerService.consumeAuditEvent(
                    txId,
                    requiredLong(entry, "accountId"),
                    requiredText(entry, "entryType"),
                    requiredDecimal(entry, "amount"),
                    requiredText(entry, "currency"),
                    requiredDecimal(entry, "beforeBalance"),
                    requiredDecimal(entry, "afterBalance"));
        }
    }

    /**
     * Consumer Group 2 - Near-real-time reconciliation check.
     * (Races the audit write; moved behind the audit write in C4.)
     */
    @KafkaListener(topics = KafkaTopics.TRANSACTION_EVENTS, groupId = KafkaTopics.RECON_GROUP, concurrency = "3")
    public void onReconciliationEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode node = objectMapper.readTree(record.value());
        reconciliationService.validateEventRealTime(requiredLong(node, "transactionId"));
    }
}
