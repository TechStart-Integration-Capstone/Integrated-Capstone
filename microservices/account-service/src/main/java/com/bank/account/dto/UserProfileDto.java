package com.bank.account.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mobile and Web customer profile representation (Phase 5).
 * Matches the /api/v1/auth/banking/me response byte-for-byte.
 */
public record UserProfileDto(
        String firstName,
        String fullName,
        String username,
        String email,
        List<AccountSummaryDto> accounts
) {
    public record AccountSummaryDto(
            long accountId,
            String accountNumber,
            String accountType,
            String currency,
            BigDecimal currentBalance,
            String status
    ) {}
}
