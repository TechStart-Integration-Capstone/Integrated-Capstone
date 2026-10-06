package com.bank.transaction.orchestrator.interest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class InterestPolicyTest {
    @ParameterizedTest
    @CsvSource({"0,0.0100", "999.99,0.0100", "999.9999,0.0100", "1000,0.0250",
            "9999.99,0.0250", "9999.9999,0.0250", "10000,0.0400", "50000,0.0400"})
    void tierBoundaries(String balance, String rate) {
        assertThat(InterestPolicy.rate("SAVINGS", new BigDecimal(balance), BigDecimal.ZERO))
                .isEqualByComparingTo(rate);
        assertThat(InterestPolicy.rate("SAVINGS_ACCOUNT", new BigDecimal(balance), BigDecimal.ZERO))
                .isEqualByComparingTo(rate);
    }

    @Test void loanUsesContractFraction() {
        assertThat(InterestPolicy.rate("LOAN", new BigDecimal("10000"), new BigDecimal("0.1800")))
                .isEqualByComparingTo("0.1800");
    }

    @Test void keepsSixDecimalPlacesWithFixed365Basis() {
        assertThat(InterestPolicy.daily(new BigDecimal("1000"), new BigDecimal("0.0250")))
                .isEqualTo(new BigDecimal("0.068493"));
        assertThat(InterestPolicy.daily(new BigDecimal("10000"), new BigDecimal("0.0400")))
                .isEqualTo(new BigDecimal("1.095890"));
        assertThat(InterestPolicy.daily(BigDecimal.ZERO, new BigDecimal("0.0400")))
                .isEqualTo(new BigDecimal("0.000000"));
    }

    @Test void rejectsInvalidInputs() {
        assertThatIllegalArgumentException().isThrownBy(() -> InterestPolicy.rate("CHECKING", BigDecimal.ONE, BigDecimal.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> InterestPolicy.rate("LOAN", BigDecimal.ONE, null));
        assertThatIllegalArgumentException().isThrownBy(() -> InterestPolicy.rate("SAVINGS", BigDecimal.ONE.negate(), BigDecimal.ZERO));
    }
}
