package com.bank.audit.service;

import com.bank.audit.model.RiskDecision;
import com.bank.audit.repository.RiskDecisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * PayPink 2.0 — Phase 6 Risk Decision Kafka Consumer.
 *
 * Listens on the {@code risk.decisions} topic published by
 * transaction-service's RiskDecisionPublisher after every /score call.
 *
 * Writes one append-only row to RISK_DECISION in PostgreSQL for every
 * scored transfer — APPROVE, REJECT, and UNAVAILABLE outcomes all recorded.
 *
 * Idempotent: duplicate events for the same reference_no are silently skipped.
 */
@Service
public class RiskDecisionConsumer {

    private static final Logger log = LoggerFactory.getLogger(RiskDecisionConsumer.class);

    private final RiskDecisionRepository riskDecisionRepository;
    private final ObjectMapper           objectMapper;

    public RiskDecisionConsumer(RiskDecisionRepository riskDecisionRepository,
                                ObjectMapper objectMapper) {
        this.riskDecisionRepository = riskDecisionRepository;
        this.objectMapper           = objectMapper;
    }

    @KafkaListener(topics = "risk.decisions", groupId = "audit-service-risk-group")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            String referenceNo = node.path("referenceNo").asText(null);
            if (referenceNo == null || referenceNo.isBlank()) {
                log.warn("[risk-decision-consumer] Missing referenceNo in event — skipped. payload={}", message);
                return;
            }

            // Idempotency: skip if already recorded for this reference
            if (riskDecisionRepository.findByReferenceNo(referenceNo).isPresent()) {
                log.info("[risk-decision-consumer] Decision already recorded for referenceNo={} — skipped.", referenceNo);
                return;
            }

            BigDecimal score     = parseBigDecimal(node, "score",     BigDecimal.ZERO);
            BigDecimal ruleScore = parseBigDecimal(node, "ruleScore", null);
            BigDecimal mlScore   = parseBigDecimal(node, "mlScore",   null);
            String     decision  = node.path("decision").asText("UNKNOWN");
            int        latencyMs = node.path("latencyMs").asInt(0);

            // reasons is a JSON array — store as JSON string
            String reasons;
            if (node.has("reasons") && node.get("reasons").isArray()) {
                reasons = objectMapper.writeValueAsString(node.get("reasons"));
            } else {
                reasons = "[]";
            }

            RiskDecision record = new RiskDecision(
                    referenceNo, score, ruleScore, mlScore, decision, reasons, latencyMs);

            riskDecisionRepository.save(record);

            log.info("[risk-decision-consumer] Saved RISK_DECISION referenceNo={} score={} decision={}",
                    referenceNo, score, decision);

        } catch (Exception ex) {
            log.error("[risk-decision-consumer] Failed to process risk.decisions event: {}", ex.getMessage());
        }
    }

    private BigDecimal parseBigDecimal(JsonNode node, String field, BigDecimal defaultValue) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return defaultValue;
        try { return new BigDecimal(n.asText()); } catch (NumberFormatException e) { return defaultValue; }
    }
}
