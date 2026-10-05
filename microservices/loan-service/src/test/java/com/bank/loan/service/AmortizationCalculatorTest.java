package com.bank.loan.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AmortizationCalculatorTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    @DisplayName("TC-EMI-01: 100,000 · 18% · 12 mo → EMI 9,168.00, schedule ends at 0.00")
    void tcEmi01_scheduleRowsAndFinalBalance() {
        assertThat(AmortizationCalculator.installment(bd("100000"), bd("18"), 12)).isEqualByComparingTo("9168.00");

        LocalDate disbursed = LocalDate.of(2026, 10, 5);
        List<AmortizationCalculator.Installment> rows =
                AmortizationCalculator.schedule(bd("100000"), bd("18"), 12, disbursed);

        assertThat(rows).hasSize(12);
        assertThat(rows.get(0).interest()).isEqualByComparingTo("1500.00");
        assertThat(rows.get(0).principal()).isEqualByComparingTo("7668.00");
        assertThat(rows.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(rows.get(11).interest()).isEqualByComparingTo("135.49");
        assertThat(rows.get(11).principal()).isEqualByComparingTo("9032.50");
        assertThat(rows.get(11).dueDate()).isEqualTo(LocalDate.of(2027, 10, 5));

        BigDecimal repaid = rows.stream().map(AmortizationCalculator.Installment::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(bd("100000.00").subtract(repaid)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("TC-EMI-02: 30,000 · 28% · 12 mo → EMI 2,895.18")
    void tcEmi02() {
        assertThat(AmortizationCalculator.installment(bd("30000"), bd("28.0"), 12)).isEqualByComparingTo("2895.18");
    }

    @Test
    @DisplayName("TC-EMI-03: 250,000 · 18% · 36 mo → EMI 9,038.10")
    void tcEmi03() {
        assertThat(AmortizationCalculator.installment(bd("250000"), bd("18.0"), 36)).isEqualByComparingTo("9038.10");
    }

    @Test
    @DisplayName("TC-EMI-04: 1,000,000 · 10.5% · 60 mo → EMI 21,493.90")
    void tcEmi04() {
        assertThat(AmortizationCalculator.installment(bd("1000000"), bd("10.5"), 60)).isEqualByComparingTo("21493.90");
    }

    @Test
    @DisplayName("Every schedule pays the principal off exactly, whatever the rounding")
    void schedulesAlwaysEndAtZero() {
        for (String[] c : new String[][]{{"30000", "28.0", "12"}, {"250000", "18.0", "36"}, {"1000000", "10.5", "60"}, {"5000", "28.0", "3"}}) {
            List<AmortizationCalculator.Installment> rows = AmortizationCalculator.schedule(
                    bd(c[0]), bd(c[1]), Integer.parseInt(c[2]), LocalDate.of(2026, 1, 31));
            BigDecimal repaid = rows.stream().map(AmortizationCalculator.Installment::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(repaid).isEqualByComparingTo(c[0]);
            assertThat(rows).allSatisfy(r -> assertThat(r.principal().scale()).isEqualTo(2));
        }
    }
}
