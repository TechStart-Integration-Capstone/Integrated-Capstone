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
 *   3. Pick the band; cap amount and term at the band limits
 *   4. Installment above affordability cap   → DECLINED (INSUFFICIENT_INCOME)
 *   5. Nothing capped → APPROVED, otherwise COUNTER_OFFER
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

    public Decision decide(int creditScore, BigDecimal monthlyIncome, BigDecimal requestedAmount,
                           int requestedTerm, boolean hasOverdueLoan) {
        Map.Entry<String, LoanProperties.Band> band = bandFor(creditScore);
        String bandName = band != null ? band.getKey() : null;

        if (hasOverdueLoan) return Decision.declined(bandName, "EXISTING_LOAN_OVERDUE");
        if (creditScore < props.getDeclineBelowScore() || band == null) return Decision.declined(bandName, "CREDIT_SCORE_TOO_LOW");

        LoanProperties.Band rules = band.getValue();
        BigDecimal amount = requestedAmount.min(rules.getMaxAmount());
        int term = Math.min(requestedTerm, rules.getMaxTerm());

        BigDecimal installment = AmortizationCalculator.installment(amount, rules.getAnnualRate(), term);
        BigDecimal cap = AmortizationCalculator.money(monthlyIncome.multiply(props.getAffordabilityRatio()));
        if (installment.compareTo(cap) > 0) return Decision.declined(bandName, "INSUFFICIENT_INCOME");

        boolean unchanged = amount.compareTo(requestedAmount) == 0 && term == requestedTerm;
        return new Decision(unchanged ? APPROVED : COUNTER_OFFER, bandName, AmortizationCalculator.money(amount), term,
                rules.getAnnualRate(), installment, null);
    }

    public Map.Entry<String, LoanProperties.Band> bandFor(int creditScore) {
        return props.getBands().entrySet().stream()
                .filter(e -> e.getValue().covers(creditScore))
                .findFirst()
                .orElse(null);
    }
}
