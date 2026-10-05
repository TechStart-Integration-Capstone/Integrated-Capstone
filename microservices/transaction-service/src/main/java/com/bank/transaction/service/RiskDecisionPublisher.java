package com.bank.transaction.service;

import com.bank.transaction.dto.RiskResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * PayPink 2.0 — Risk Decision Publisher (Phase 6)
 *
 * Publishes a lightweight event to the {@code risk.decisions} Kafka topic
 * after every risk-engine scoring call — both APPROVE and REJECT outcomes.
 *
 * Called by RemittanceOrchestratorService immediately after evaluateRisk()
 * returns and before any reject exception is thrown, so that REJECTED
 * transfers are also captured in the PostgreSQL RISK_DECISION audit table.
 *
 * Fire-and-forget: any Kafka failure is logged and swallowed.
 * The orchestrator's primary flow must never be blocked by an audit write.
 */
@Component
public class RiskDecisionPublisher {

    private static final Logger log = LoggerFactory.getLogger(RiskDecisionPublisher.class);
    static final String TOPIC = "risk.decisions";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RiskDecisionPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    /**
     * Publish one risk decision event.
     *
     * @param referenceNo  Remittance reference number — used as Kafka message key
     *                     so all retries for the same transfer land on the same partition.
     * @param risk         Full RiskResult from the risk engine (may carry null ruleScore/mlScore
     *                     if the engine is running an older version — handled gracefully).
     */
    public void publish(String referenceNo, RiskResult risk) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("referenceNo",  referenceNo);
            payload.put("score",        risk.score()     != null ? risk.score().toPlainString()     : "0");
            payload.put("ruleScore",    risk.ruleScore() != null ? risk.ruleScore().toPlainString() : null);
            payload.put("mlScore",      risk.mlScore()   != null ? risk.mlScore().toPlainString()   : null);
            payload.put("decision",     risk.decision());
            payload.put("reasons",      risk.reasons());
            payload.put("latencyMs",    risk.latencyMs() != null ? risk.latencyMs().intValue()      : 0);

            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, referenceNo, json);

            log.info("[risk-decision-publisher] Published decision referenceNo={} score={} decision={}",
                    referenceNo, risk.score(), risk.decision());

        } catch (Exception ex) {
            // Best-effort — never block the saga for an audit write
            log.error("[risk-decision-publisher] Failed to publish risk decision for referenceNo={}: {}",
                    referenceNo, ex.getMessage());
        }
    }
}
