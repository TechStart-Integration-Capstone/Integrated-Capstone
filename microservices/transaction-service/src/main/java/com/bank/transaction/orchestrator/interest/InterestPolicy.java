package com.bank.transaction.orchestrator.interest;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class InterestPolicy {
    private InterestPolicy() {}

    public static BigDecimal rate(String accountType, BigDecimal balance, BigDecimal contractRate) {
        if (balance.signum() < 0) throw new IllegalArgumentException("Negative EOD balance");
        if ("LOAN".equals(accountType)) {
            if (contractRate == null || contractRate.signum() < 0)
                throw new IllegalArgumentException("Invalid loan contract rate");
            return contractRate;
        }
        if (!isSavings(accountType)) throw new IllegalArgumentException("Unsupported interest account type");
        if (balance.compareTo(new BigDecimal("1000")) < 0) return new BigDecimal("0.0100");
        if (balance.compareTo(new BigDecimal("10000")) < 0) return new BigDecimal("0.0250");
        return new BigDecimal("0.0400");
    }

    public static BigDecimal daily(BigDecimal balance, BigDecimal rate) {
        return balance.multiply(rate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    }

    public static boolean isSavings(String type) {
        return "SAVINGS".equals(type) || "SAVINGS_ACCOUNT".equals(type);
    }
}
