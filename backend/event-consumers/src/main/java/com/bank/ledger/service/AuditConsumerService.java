package com.bank.ledger.service;

import com.bank.ledger.model.postgres.LedgerMutationAudit;
import com.bank.ledger.repository.postgres.LedgerMutationAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AuditConsumerService {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumerService.class);
    private final LedgerMutationAuditRepository ledgerMutationAuditRepository;

    public AuditConsumerService(LedgerMutationAuditRepository ledgerMutationAuditRepository) {
        this.ledgerMutationAuditRepository = ledgerMutationAuditRepository;
    }

    /**
     * Kafka Consumer for Audit Topic:
     * Appends immutable audit entry to PostgreSQL LEDGER_MUTATION_AUDIT table.
     * Idempotently skips duplicates and safely handles unique constraint collisions.
     */

    public LedgerMutationAudit consumeAuditEvent(Long transactionId, Long accountId, String entryType,
                                                 BigDecimal amount, String currency, BigDecimal beforeBalance,
                                                 BigDecimal afterBalance) {
        if (transactionId == null || accountId == null) {
            return null;
        }

        // Idempotency check: skip if leg already recorded
        if (ledgerMutationAuditRepository.existsByTransactionIdAndAccountId(transactionId, accountId)) {
            log.info("Audit leg already exists for txId={}, accountId={}. Skipping duplicate.", transactionId, accountId);
            return ledgerMutationAuditRepository.findByTransactionIdAndAccountId(transactionId, accountId).orElse(null);
        }

        try {
            LedgerMutationAudit audit = new LedgerMutationAudit(
                    transactionId,
                    accountId,
                    entryType,
                    amount,
                    currency,
                    beforeBalance,
                    afterBalance
            );
            return ledgerMutationAuditRepository.saveAndFlush(audit);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Unique constraint collision for txId={}, accountId={}. Handled idempotently.", transactionId, accountId);
            return ledgerMutationAuditRepository.findByTransactionIdAndAccountId(transactionId, accountId).orElse(null);
        }
    }
}
