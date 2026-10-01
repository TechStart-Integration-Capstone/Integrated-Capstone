package com.bank.ledger.service;

import com.bank.ledger.dto.MutationRequest;
import com.bank.ledger.exception.BusinessException;
import com.bank.ledger.model.oracle.TransactionRecord;
import com.bank.ledger.repository.oracle.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class FailedTransactionRecorder {

    private static final Logger log = LoggerFactory.getLogger(FailedTransactionRecorder.class);
    private final TransactionRepository transactionRepository;

    public FailedTransactionRecorder(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Persists a failed transaction attempt in an isolated, clean transaction.
     * Called after the main Oracle mutation transaction has rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionRecord recordFailure(MutationRequest request, BusinessException ex) {
        try {
            String referenceNo = "TX-FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Long fromAccountId = request.getAccountId() != null ? request.getAccountId() : 0L;
            BigDecimal amount = request.getMutationAmount() != null ? request.getMutationAmount() : BigDecimal.ZERO;
            String currency = request.getCurrency() != null ? request.getCurrency() : "PHP";
            String txType = request.getTransactionType() != null ? request.getTransactionType() : "DEBIT";

            TransactionRecord failedRecord = new TransactionRecord(
                    fromAccountId,
                    request.getTargetAccountId(),
                    amount,
                    currency,
                    currency,
                    txType,
                    referenceNo,
                    "FAILED",
                    ex.getReasonCode()
            );

            failedRecord.setOperation("CREDIT".equalsIgnoreCase(request.getOperation()) ? "CREDIT" : "DEBIT");
            TransactionRecord saved = transactionRepository.save(failedRecord);
            log.warn("Recorded FAILED transaction: ref={}, fromAccount={}, reason={}", 
                    referenceNo, fromAccountId, ex.getReasonCode());
            return saved;
        } catch (Exception e) {
            log.error("Failed to persist failed transaction record: {}", e.getMessage(), e);
            return null;
        }
    }
}
