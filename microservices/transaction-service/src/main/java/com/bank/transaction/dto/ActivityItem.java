package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Clean CQRS activity record for customer transaction feeds (Phase 4).
 * Matches the mobile and web client expectations.
 */
public record ActivityItem(
        Long transactionId,
        Long accountId,
        String accountNumber,
        BigDecimal amount,
        String currency,
        String transactionType,
        String operation,
        String reference,
        String status,
        LocalDateTime transactionDate,
        String counterpartyName,
        String counterpartyAccountNumber
) {}
