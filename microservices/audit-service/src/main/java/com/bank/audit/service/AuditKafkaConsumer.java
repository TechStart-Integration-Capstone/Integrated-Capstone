package com.bank.audit.service;

import com.bank.audit.model.LedgerMutationAudit;
import com.bank.audit.repository.LedgerMutationAuditRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuditKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditKafkaConsumer.class);

    private final LedgerMutationAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public AuditKafkaConsumer(LedgerMutationAuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "ledger.transaction.events", groupId = "audit-service-group")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            Long transactionId  = node.has("transactionId")  ? node.get("transactionId").asLong()  : null;
            Long accountId      = node.has("accountId")      ? node.get("accountId").asLong()      : null;
            String operation    = node.has("operation")      ? node.get("operation").asText()      : "DEBIT";
            String currency     = node.has("currency")       ? node.get("currency").asText()       : "PHP";
            BigDecimal amount        = node.has("amount")        ? new BigDecimal(node.get("amount").asText())        : BigDecimal.ZERO;
            BigDecimal beforeBalance = node.has("beforeBalance") ? new BigDecimal(node.get("beforeBalance").asText()) : BigDecimal.ZERO;
            BigDecimal afterBalance  = node.has("afterBalance")  ? new BigDecimal(node.get("afterBalance").asText())  : BigDecimal.ZERO;

            if (transactionId == null || accountId == null) {
                log.warn("[audit-service] Missing transactionId or accountId in event — skipped.");
                return;
            }

            // Idempotency: skip if already audited
            if (auditRepository.findByTransactionId(transactionId).isPresent()) {
                log.info("[audit-service] Audit already exists for transactionId {} — skipped.", transactionId);
                return;
            }

            auditRepository.save(new LedgerMutationAudit(
                    transactionId, accountId, operation, amount, currency, beforeBalance, afterBalance));

            log.info("[audit-service] Audit record saved for transactionId {}", transactionId);

        } catch (Exception e) {
            log.error("[audit-service] Failed to process audit event: {}", e.getMessage());
        }
    }
}
