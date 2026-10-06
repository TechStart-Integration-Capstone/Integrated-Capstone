package com.bank.loan.service;

import com.bank.loan.config.LoanProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Instant loan decision from the hardcoded credit score. Pure logic, no I/O.
 *
 * Steps, in order:
 *   1. Customer has an OVERDUE loan          → DECLINED (EXISTING_LOAN_OVERDUE)
 *   2. credit_score below decline threshold  → DECLINED (CREDIT_SCORE_TOO_LOW)
 *   3. Pick the band. Its max amount is the customer's total credit limit: what they still owe on open
 *      loans is taken off it. Less than the minimum loan left → DECLINED (CREDIT_LIMIT_REACHED)
 *   4. Cap amount at the remaining limit and term at the band limit
 *   5. Installment above affordability cap   → DECLINED (INSUFFICIENT_INCOME)
 *   6. Nothing capped → APPROVED, otherwise COUNTER_OFFER
 */
@Component
public class LoanDecisionEngine {

    public static final String APPROVED = "APPROVED";
    public static final String COUNTER_OFFER = "COUNTER_OFFER";
    public static final String DECLINED = "DECLINED";

    private final LoanProperties props;

    public LoanDecisionEngine(LoanProperties props) {
        this.props = props;
    }

    public record Decision(String decision, String band, BigDecimal amount, Integer termMonths,
                           BigDecimal annualRate, BigDecimal monthlyInstallment, String declineReason) {
        static Decision declined(String band, String reason) {
            return new Decision(DECLINED, band, null, null, null, null, reason);
        }
        public boolean isDeclined() { return DECLINED.equals(decision); }
    }

    /** @param existingDebt principal still owed on open loans plus offers being disbursed */
    public Decision decide(int creditScore, BigDecimal monthlyIncome, BigDecimal requestedAmount,
                           int requestedTerm, boolean hasOverdueLoan, BigDecimal existingDebt) {
        Map.Entry<String, LoanProperties.Band> band = bandFor(creditScore);
        String bandName = band != null ? band.getKey() : null;

        if (hasOverdueLoan) return Decision.declined(bandName, "EXISTING_LOAN_OVERDUE");
        if (creditScore < props.getDeclineBelowScore() || band == null) return Decision.declined(bandName, "CREDIT_SCORE_TOO_LOW");

        LoanProperties.Band rules = band.getValue();
        BigDecimal available = availableCredit(rules, existingDebt);
        if (available.compareTo(props.getMinAmount()) < 0) return Decision.declined(bandName, "CREDIT_LIMIT_REACHED");

        BigDecimal amount = requestedAmount.min(available);
        int term = Math.min(requestedTerm, rules.getMaxTerm());

        BigDecimal installment = AmortizationCalculator.installment(amount, rules.getAnnualRate(), term);
        BigDecimal cap = AmortizationCalculator.money(monthlyIncome.multiply(props.getAffordabilityRatio()));
        if (installment.compareTo(cap) > 0) return Decision.declined(bandName, "INSUFFICIENT_INCOME");

        boolean unchanged = amount.compareTo(requestedAmount) == 0 && term == requestedTerm;
        return new Decision(unchanged ? APPROVED : COUNTER_OFFER, bandName, AmortizationCalculator.money(amount), term,
                rules.getAnnualRate(), installment, null);
    }

    /** Band limit minus existing debt, never below zero. */
    public static BigDecimal availableCredit(LoanProperties.Band rules, BigDecimal existingDebt) {
        BigDecimal debt = existingDebt != null ? existingDebt : BigDecimal.ZERO;
        return AmortizationCalculator.money(rules.getMaxAmount().subtract(debt).max(BigDecimal.ZERO));
    }

    public Map.Entry<String, LoanProperties.Band> bandFor(int creditScore) {
        return props.getBands().entrySet().stream()
                .filter(e -> e.getValue().covers(creditScore))
                .findFirst()
                .orElse(null);
    }
}
