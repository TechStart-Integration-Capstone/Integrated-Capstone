package com.bank.loan;

import com.bank.loan.config.LoanProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wiring + HTTP edge checks that never reach the database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:loans;MODE=MSSQLServer",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class LoanServiceApplicationTest {

    @Autowired LoanProperties props;
    @Autowired MockMvc mvc;

    @Test
    void loanRulesBindFromApplicationYml() {
        assertThat(props.getBankAccountNo()).isEqualTo("PH1000000LOAN");
        assertThat(props.getBands()).containsOnlyKeys("LOW", "NORMAL", "HIGH");
        assertThat(props.getBands().get("NORMAL").getMaxAmount()).isEqualByComparingTo("250000");
        assertThat(props.getBands().get("HIGH").getAnnualRate()).isEqualByComparingTo("10.5");
        assertThat(props.getBands().get("LOW").getMaxTerm()).isEqualTo(12);
        assertThat(props.getPenaltyRate()).isEqualByComparingTo("0.02");
    }

    @Test
    void missingIdentityHeader_is401Problem() throws Exception {
        mvc.perform(post("/loans/applications").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNo\":\"001133218709\",\"amount\":250000.00,\"termMonths\":36}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.paypink.ph/errors/unauthorized"));
    }

    @Test
    void invalidBody_is400ValidationError() throws Exception {
        mvc.perform(post("/loans/applications").header("X-Auth-Customer-Id", "2").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNo\":\"001133218709\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.paypink.ph/errors/validation-error"));
    }

    @Test
    void eodWithoutAdminRole_is403() throws Exception {
        mvc.perform(post("/loans/eod/run").param("businessDate", "2026-11-06")
                        .header("X-Auth-Customer-Id", "2").header("X-Auth-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden());
    }
}
