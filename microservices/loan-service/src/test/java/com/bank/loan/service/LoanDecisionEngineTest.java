package com.bank.loan.service;

import com.bank.loan.config.LoanProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LoanDecisionEngineTest {

    private final LoanDecisionEngine engine = new LoanDecisionEngine(LoanProperties.defaults());

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    @DisplayName("Credit limit: NORMAL limit 250,000 with 200,000 still owed → counter-offer capped at 50,000")
    void creditLimit_capsAtRemaining() {
        LoanDecisionEngine.Decision d = engine.decide(670, bd("45000"), bd("100000"), 12, false, bd("200000"));

        assertThat(d.decision()).isEqualTo("COUNTER_OFFER");
        assertThat(d.amount()).isEqualByComparingTo("50000.00");
        assertThat(d.termMonths()).isEqualTo(12);
    }

    @Test
    @DisplayName("Credit limit: less than the 5,000 minimum left → DECLINED CREDIT_LIMIT_REACHED")
    void creditLimit_reached() {
        LoanDecisionEngine.Decision d = engine.decide(520, bd("20000"), bd("10000"), 12, false, bd("26000"));

        assertThat(d.decision()).isEqualTo("DECLINED");
        assertThat(d.band()).isEqualTo("LOW");
        assertThat(d.declineReason()).isEqualTo("CREDIT_LIMIT_REACHED");
    }

    @Test
    @DisplayName("TC-LD-01: NORMAL customer asks 250,000 / 36 → APPROVED 18%, 9,038.10")
    void tcLd01_normalApproved() {
        LoanDecisionEngine.Decision d = engine.decide(670, bd("45000"), bd("250000.00"), 36, false, BigDecimal.ZERO);

        assertThat(d.decision()).isEqualTo("APPROVED");
        assertThat(d.band()).isEqualTo("NORMAL");
        assertThat(d.amount()).isEqualByComparingTo("250000.00");
        assertThat(d.termMonths()).isEqualTo(36);
        assertThat(d.annualRate()).isEqualByComparingTo("18.0");
        assertThat(d.monthlyInstallment()).isEqualByComparingTo("9038.10");
    }

    @Test
    @DisplayName("TC-LD-02: HIGH customer asks 1,000,000 / 60 → APPROVED 10.5%, 21,493.90")
    void tcLd02_highApproved() {
        LoanDecisionEngine.Decision d = engine.decide(800, bd("150000"), bd("1000000"), 60, false, BigDecimal.ZERO);

        assertThat(d.decision()).isEqualTo("APPROVED");
        assertThat(d.band()).isEqualTo("HIGH");
        assertThat(d.annualRate()).isEqualByComparingTo("10.5");
        assertThat(d.monthlyInstallment()).isEqualByComparingTo("21493.90");
    }

    @Test
    @DisplayName("TC-LD-03: LOW customer asks 100,000 / 24 → COUNTER_OFFER 30,000 / 12 at 28%, 2,895.18")
    void tcLd03_lowCounterOffer() {
        LoanDecisionEngine.Decision d = engine.decide(520, bd("20000"), bd("100000"), 24, false, BigDecimal.ZERO);

        assertThat(d.decision()).isEqualTo("COUNTER_OFFER");
        assertThat(d.band()).isEqualTo("LOW");
        assertThat(d.amount()).isEqualByComparingTo("30000.00");
        assertThat(d.termMonths()).isEqualTo(12);
        assertThat(d.annualRate()).isEqualByComparingTo("28.0");
        assertThat(d.monthlyInstallment()).isEqualByComparingTo("2895.18");
    }

    @Test
    @DisplayName("TC-LD-04: NORMAL customer asks 500,000 / 36 → COUNTER_OFFER 250,000 / 36")
    void tcLd04_normalCounterOffer() {
        LoanDecisionEngine.Decision d = engine.decide(670, bd("45000"), bd("500000"), 36, false, BigDecimal.ZERO);

        assertThat(d.decision()).isEqualTo("COUNTER_OFFER");
        assertThat(d.amount()).isEqualByComparingTo("250000.00");
        assertThat(d.termMonths()).isEqualTo(36);
    }

    @Test
    @DisplayName("TC-LD-05: credit_score 480 → DECLINED CREDIT_SCORE_TOO_LOW")
    void tcLd05_scoreTooLow() {
        LoanDecisionEngine.Decision d = engine.decide(480, bd("20000"), bd("10000"), 12, false, BigDecimal.ZERO);

        assertThat(d.decision()).isEqualTo("DECLINED");
        assertThat(d.declineReason()).isEqualTo("CREDIT_SCORE_TOO_LOW");
        assertThat(d.amount()).isNull();
    }

    @Test
    @DisplayName("An OVERDUE loan declines any new application first")
    void overdueLoan_declined() {
        LoanDecisionEngine.Decision d = engine.decide(800, bd("150000"), bd("10000"), 12, true, BigDecimal.ZERO);

        assertThat(d.decision()).isEqualTo("DECLINED");
        assertThat(d.declineReason()).isEqualTo("EXISTING_LOAN_OVERDUE");
    }

    @Test
    @DisplayName("Installment above 30% of income → DECLINED INSUFFICIENT_INCOME")
    void installmentAboveCap_declined() {
        // NORMAL band, 250,000 / 36 → 9,038.10 > 30% of 20,000 (6,000)
        LoanDecisionEngine.Decision d = engine.decide(670, bd("20000"), bd("250000"), 36, false, BigDecimal.ZERO);

        assertThat(d.decision()).isEqualTo("DECLINED");
        assertThat(d.declineReason()).isEqualTo("INSUFFICIENT_INCOME");
    }
}
