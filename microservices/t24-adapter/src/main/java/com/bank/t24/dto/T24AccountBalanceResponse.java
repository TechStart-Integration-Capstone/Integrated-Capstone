package com.bank.t24.dto;

import java.math.BigDecimal;

public record T24AccountBalanceResponse(
        Long accountId,
        String accountNumber,
        String accountType,
        String currency,
        BigDecimal currentBalance,
        BigDecimal heldBalance,
        BigDecimal availableBalance,
        String status
) {}
