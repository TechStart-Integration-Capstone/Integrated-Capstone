package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Admin transaction feed row for the Operations Desk (Phase 4).
 */
public record AdminTransactionRow(
        String transactionId,
        String referenceNo,
        OffsetDateTime transactionDate,
        String accountNumber,
        String transactionType,
        String operation,
        BigDecimal amount,
        String currency,
        String status,
        String internalStatus,
        String currentService,
        String reason,
        String targetAccountNumber
) {
    public AdminTransactionRow(
            String transactionId,
            String referenceNo,
            OffsetDateTime transactionDate,
            String accountNumber,
            String transactionType,
            String operation,
            BigDecimal amount,
            String currency,
            String status) {
        this(transactionId, referenceNo, transactionDate, accountNumber, transactionType,
                operation, amount, currency, status, null, null, null, null);
    }
}
