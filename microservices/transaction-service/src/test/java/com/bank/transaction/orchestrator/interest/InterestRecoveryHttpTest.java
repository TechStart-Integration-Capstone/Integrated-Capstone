package com.bank.transaction.orchestrator.interest;

import com.bank.transaction.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InterestRecoveryHttpTest {
    private final InterestEodService service = mock(InterestEodService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new InterestEodController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void validBackfillBindsHistoricalDataAndUsesGatewayIdentity() throws Exception {
        var day = LocalDate.of(2026, 9, 25);
        var id = java.util.UUID.randomUUID();
        when(service.prepareBackfill(eq(day), any(), eq("aly")))
                .thenReturn(new InterestAccrualStore.Proposal(id, day, "aly", null, "hash", null, null, null, null));
        mvc.perform(post("/api/v1/interest/eod/resolve").param("businessDate", day.toString())
                .header("X-Auth-Roles", "ROLE_ADMIN").header("X-Auth-Username", "aly")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"mode":"BACKFILL","reason":"Complete historical export verified","sourceReference":"export-25",
                     "confirmed":true,"accounts":[{"accountId":123,"accountType":"SAVINGS_ACCOUNT","eodBalance":1000.0000}]}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("PENDING_APPROVAL"));
        verify(service).prepareBackfill(eq(day), argThat(request -> request.accounts().get(0).eodBalance()
                .compareTo(new java.math.BigDecimal("1000")) == 0), eq("aly"));
    }

    @Test void evenConfirmedWaiverAndInvalidNestedSnapshotReturn400() throws Exception {
        for (String body : java.util.List.of(
                "{\"mode\":\"WAIVER\",\"reason\":\"approval\",\"sourceReference\":\"case-1\",\"confirmed\":true,\"accounts\":[]}",
                "{\"mode\":\"BACKFILL\",\"reason\":\"export\",\"sourceReference\":\"case-1\",\"confirmed\":true,\"accounts\":[{\"accountId\":0,\"accountType\":\"SAVINGS\",\"eodBalance\":-10}]}")) {
            mvc.perform(post("/api/v1/interest/eod/resolve").param("businessDate", "2026-09-25")
                    .header("X-Auth-Roles", "ROLE_ADMIN").header("X-Auth-Username", "aly")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void missingDaysEndpointRejectsCustomer() throws Exception {
        mvc.perform(get("/api/v1/interest/eod/missing").param("periodEnd", "2026-09-30")
                .header("X-Auth-Roles", "ROLE_CUSTOMER")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void overviewIsAdminOnlyAndApprovalUsesActualGatewayIdentity() throws Exception {
        mvc.perform(get("/api/v1/interest/eod/overview").header("X-Auth-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        var id = java.util.UUID.randomUUID();
        when(service.approveBackfill(eq(id), any(), eq("aly")))
                .thenReturn(new InterestEodService.Result(LocalDate.of(2026, 9, 25), 1, false));
        mvc.perform(post("/api/v1/interest/eod/backfills/" + id + "/approve")
                .header("X-Auth-Roles", "ROLE_ADMIN").header("X-Auth-Username", "aly")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reviewed original source\",\"confirmed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("accounts").value(1));
        verify(service).approveBackfill(eq(id), argThat(a -> a.confirmed()), eq("aly"));
    }

    @Test void approvalAndReviewRequireAdminAndApprovalRequiresIdentityAndConfirmation() throws Exception {
        String path = "/api/v1/interest/eod/backfills/" + java.util.UUID.randomUUID();
        mvc.perform(get(path).header("X-Auth-Roles", "ROLE_CUSTOMER")).andExpect(status().isForbidden());
        mvc.perform(post(path + "/approve").header("X-Auth-Roles", "ROLE_CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"verified\",\"confirmed\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/approve").header("X-Auth-Roles", "ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"verified\",\"confirmed\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/approve").header("X-Auth-Roles", "ROLE_ADMIN").header("X-Auth-Username", "checker")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"verified\",\"confirmed\":false}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
