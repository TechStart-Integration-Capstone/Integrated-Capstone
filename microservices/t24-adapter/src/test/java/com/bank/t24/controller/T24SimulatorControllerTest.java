package com.bank.t24.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class T24SimulatorControllerTest {

    @Test
    void httpResponsesExplicitlyUseJsonForSuccessRejectionAndReplay() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new T24SimulatorController()).build();
        String valid = """
                {"referenceNo":"TX-HTTP","ofsMessage":"FUNDS.TRANSFER,TX-HTTP/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100002,AMOUNT::222.00,CURRENCY::PHP"}
                """;
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/ofs/process").contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.ALL).content(valid))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("POSTED"));
        }
        mvc.perform(post("/ofs/process").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.ALL).content("{\"referenceNo\":\"TX-BAD\",\"ofsMessage\":\"invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private T24SimulatorController controller;

    @BeforeEach
    void setUp() {
        controller = new T24SimulatorController();
        controller.resetSimulator();
    }

    @Test
    @DisplayName("Should accept and return POSTED when OFS is valid and accounts are ACTIVE")
    void testValidOfsSuccess() {
        String ofs = "FUNDS.TRANSFER,PAYPINK-TX-101/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100002,AMOUNT::1500.00,CURRENCY::PHP";
        Map<String, Object> payload = Map.of(
                "referenceNo", "TX-101",
                "ofsMessage", ofs
        );

        ResponseEntity<Map<String, Object>> response = controller.processOfs(payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("POSTED");
        assertThat((String) body.get("ofsResponse")).endsWith("/1");
        assertThat((String) body.get("ftReference")).startsWith("FT20261004");
    }

    @Test
    @DisplayName("Should reject with OFS /-1 when OFS syntax is invalid (missing amount)")
    void testInvalidOfsMissingAmount() {
        String ofs = "FUNDS.TRANSFER,PAYPINK-TX-102/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100002,CURRENCY::PHP";
        Map<String, Object> payload = Map.of(
                "referenceNo", "TX-102",
                "ofsMessage", ofs
        );

        ResponseEntity<Map<String, Object>> response = controller.processOfs(payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("REJECTED");
        assertThat((String) body.get("ofsResponse")).endsWith("/-1");
        assertThat((String) body.get("reason")).contains("AMOUNT must be greater than zero");
    }

    @Test
    @DisplayName("Should reject with OFS /-1 when debit and credit accounts are identical")
    void testInvalidOfsSameAccount() {
        String ofs = "FUNDS.TRANSFER,PAYPINK-TX-103/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100001,AMOUNT::500.00,CURRENCY::PHP";
        Map<String, Object> payload = Map.of(
                "referenceNo", "TX-103",
                "ofsMessage", ofs
        );

        ResponseEntity<Map<String, Object>> response = controller.processOfs(payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("REJECTED");
        assertThat((String) body.get("ofsResponse")).endsWith("/-1");
        assertThat((String) body.get("reason")).contains("identical");
    }

    @Test
    @DisplayName("Should reject with OFS /-1 when debit account is registered as CLOSED")
    void testClosedDebitAccount() {
        controller.setAccountStatus(Map.of("accountNo", "1000100001", "status", "CLOSED"));

        String ofs = "FUNDS.TRANSFER,PAYPINK-TX-104/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100002,AMOUNT::500.00,CURRENCY::PHP";
        Map<String, Object> payload = Map.of(
                "referenceNo", "TX-104",
                "ofsMessage", ofs
        );

        ResponseEntity<Map<String, Object>> response = controller.processOfs(payload);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("REJECTED");
        assertThat((String) body.get("ofsResponse")).endsWith("/-1");
        assertThat((String) body.get("reason")).contains("CLOSED");
    }

    @Test
    @DisplayName("Should reject with OFS /-1 when target credit account is registered as FROZEN")
    void testFrozenCreditAccount() {
        controller.setAccountStatus(Map.of("accountNo", "1000100002", "status", "FROZEN"));

        String ofs = "FUNDS.TRANSFER,PAYPINK-TX-105/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100002,AMOUNT::500.00,CURRENCY::PHP";
        Map<String, Object> payload = Map.of(
                "referenceNo", "TX-105",
                "ofsMessage", ofs
        );

        ResponseEntity<Map<String, Object>> response = controller.processOfs(payload);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("REJECTED");
        assertThat((String) body.get("ofsResponse")).endsWith("/-1");
        assertThat((String) body.get("reason")).contains("FROZEN");
    }

    @Test
    @DisplayName("Should reject with OFS /-1 for convention-based CLOSED account")
    void testConventionClosedAccount() {
        String ofs = "FUNDS.TRANSFER,PAYPINK-TX-106/I/PROCESS,,DEBIT.ACCT.NO::ACC-CLOSED-01,CREDIT.ACCT.NO::1000100002,AMOUNT::500.00,CURRENCY::PHP";
        Map<String, Object> payload = Map.of(
                "referenceNo", "TX-106",
                "ofsMessage", ofs
        );

        ResponseEntity<Map<String, Object>> response = controller.processOfs(payload);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("REJECTED");
        assertThat((String) body.get("ofsResponse")).endsWith("/-1");
        assertThat((String) body.get("reason")).contains("CLOSED");
    }

    @Test
    @DisplayName("Should reject with OFS /-1 for convention-based FROZEN account")
    void testConventionFrozenAccount() {
        String ofs = "FUNDS.TRANSFER,PAYPINK-TX-107/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::ACC-FROZEN-02,AMOUNT::500.00,CURRENCY::PHP";
        Map<String, Object> payload = Map.of(
                "referenceNo", "TX-107",
                "ofsMessage", ofs
        );

        ResponseEntity<Map<String, Object>> response = controller.processOfs(payload);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("REJECTED");
        assertThat((String) body.get("ofsResponse")).endsWith("/-1");
        assertThat((String) body.get("reason")).contains("FROZEN");
    }

    @Test
    @DisplayName("Should be 100% deterministic over multiple invocations (no probabilistic flips)")
    void testDeterministicExecution() {
        for (int i = 0; i < 50; i++) {
            String ofs = "FUNDS.TRANSFER,PAYPINK-DET-" + i + "/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100002,AMOUNT::250.00,CURRENCY::PHP";
            ResponseEntity<Map<String, Object>> resp = controller.processOfs(Map.of(
                    "referenceNo", "TX-DET-" + i,
                    "ofsMessage", ofs
            ));
            assertThat(resp.getBody().get("status")).isEqualTo("POSTED");
            assertThat((String) resp.getBody().get("ofsResponse")).endsWith("/1");
        }
    }
}
