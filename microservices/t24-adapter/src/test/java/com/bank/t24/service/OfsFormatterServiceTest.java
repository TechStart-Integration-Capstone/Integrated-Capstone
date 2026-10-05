package com.bank.t24.service;

import com.bank.t24.dto.T24TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OfsFormatterServiceTest {

    private OfsFormatterService formatter;

    @BeforeEach
    void setUp() {
        formatter = new OfsFormatterService();
    }

    @Test
    @DisplayName("Should format valid transfer request into Temenos T24 OFS string")
    void testBuildFundsTransferOfs() {
        T24TransferRequest request = new T24TransferRequest(
                "TX-PH-12345",
                "1000100001",
                "1000100002",
                new BigDecimal("15000.00"),
                "PHP"
        );

        String ofs = formatter.buildFundsTransferOfs(request);

        assertThat(ofs).contains("FUNDS.TRANSFER,PAYPINK-TX-PH-12345/I/PROCESS");
        assertThat(ofs).contains("DEBIT.ACCT.NO::1000100001");
        assertThat(ofs).contains("CREDIT.ACCT.NO::1000100002");
        assertThat(ofs).contains("AMOUNT::15000.00");
        assertThat(ofs).contains("CURRENCY::PHP");
    }
}
