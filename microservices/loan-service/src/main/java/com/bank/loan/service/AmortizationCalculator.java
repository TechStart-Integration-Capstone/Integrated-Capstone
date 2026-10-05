package com.bank.loan.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Equal-installment (EMI) amortization. BigDecimal only; money rounded HALF_EVEN to 2 dp.
 *
 *   r   = annual_rate / 100 / 12
 *   EMI = P × r × (1 + r)^n / ((1 + r)^n − 1)
 *
 * Schedule row i: interest = round(balance × r), principal = EMI − interest.
 * The last row takes the remaining balance as principal, so rounding is absorbed and the balance ends at 0.00.
 */
public final class AmortizationCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    private AmortizationCalculator() {}

    public record Installment(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest) {
        public BigDecimal total() { return principal.add(interest); }
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent.divide(TWELVE_HUNDRED, MC);
    }

    public static BigDecimal installment(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (months <= 0) throw new IllegalArgumentException("Term must be positive");
        BigDecimal r = monthlyRate(annualRatePercent);
        if (r.signum() == 0) {
            return money(principal.divide(BigDecimal.valueOf(months), MC));
        }
        BigDecimal growth = BigDecimal.ONE.add(r).pow(months, MC);
        BigDecimal emi = principal.multiply(r, MC).multiply(growth, MC).divide(growth.subtract(BigDecimal.ONE), MC);
        return money(emi);
    }

    public static List<Installment> schedule(BigDecimal principal, BigDecimal annualRatePercent, int months, LocalDate disbursedDate) {
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal emi = installment(principal, annualRatePercent, months);
        BigDecimal balance = money(principal);
        List<Installment> rows = new ArrayList<>(months);
        for (int i = 1; i <= months; i++) {
            BigDecimal interest = money(balance.multiply(r, MC));
            BigDecimal principalPart = i == months ? balance : emi.subtract(interest);
            balance = balance.subtract(principalPart);
            rows.add(new Installment(i, disbursedDate.plusMonths(i), principalPart, interest));
        }
        return rows;
    }
}
