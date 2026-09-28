package com.bank.analytics.service;

import com.bank.analytics.dto.AnalyticsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Kafka consumer for the analytics-service.
 *
 * Listens to the same ledger.transaction.events topic as the audit,
 * notification, and reconciliation services — each with its own
 * independent consumer group so they all receive every event.
 *
 * Consumer group: analytics-service-group
 *
 * On each message:
 *   1. Deserialise the JSON payload into an AnalyticsEvent
 *   2. Hand off to AnalyticsAggregator for lock-free in-memory update
 *
 * Errors are caught and logged — a bad message is skipped rather than
 * causing a consumer group lag by re-queuing indefinitely.
 */
@Service
public class AnalyticsKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsKafkaConsumer.class);

    private final AnalyticsAggregator aggregator;
    private final ObjectMapper        objectMapper;

    public AnalyticsKafkaConsumer(AnalyticsAggregator aggregator,
                                  ObjectMapper objectMapper) {
        this.aggregator   = aggregator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics   = "ledger.transaction.events",
            groupId  = "analytics-service-group"
    )
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            AnalyticsEvent event = new AnalyticsEvent();
            event.setTransactionId(nodeAsLong(node,   "transactionId"));
            event.setReferenceNo(nodeAsString(node,   "referenceNo"));
            event.setAccountId(nodeAsLong(node,       "accountId"));
            event.setCustomerId(nodeAsLong(node,      "customerId"));
            event.setOperation(nodeAsString(node,     "operation"));
            event.setAmount(nodeAsBigDecimal(node,    "amount"));
            event.setCurrency(nodeAsString(node,      "currency"));
            event.setBeforeBalance(nodeAsBigDecimal(node, "beforeBalance"));
            event.setAfterBalance(nodeAsBigDecimal(node,  "afterBalance"));
            event.setTimestamp(nodeAsString(node,     "timestamp"));

            aggregator.ingest(event);

            log.debug("[analytics-service] Ingested txId={} op={} amount={}",
                    event.getTransactionId(), event.getOperation(), event.getAmount());

        } catch (Exception ex) {
            log.error("[analytics-service] Failed to process event — skipping: {}", ex.getMessage());
        }
    }

    // ── Safe node extraction helpers ──────────────────────────────────────────

    private Long nodeAsLong(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asLong() : null;
    }

    private String nodeAsString(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : null;
    }

    private BigDecimal nodeAsBigDecimal(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        try {
            return new BigDecimal(node.get(field).asText());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
