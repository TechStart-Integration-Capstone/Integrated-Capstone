package com.bank.auth.admin;

import com.bank.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionMonitorControllerTest {
    private static final String SECRET = "test-signing-key-with-at-least-thirty-two-characters";
    private static final String URL = "/api/v1/auth/admin/transactions/today";
    private final JwtTokenProvider tokens = new JwtTokenProvider(SECRET, 60000);
    private final TransactionMonitorService monitor = mock(TransactionMonitorService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TransactionMonitorController(tokens, monitor)).build();
    }

    @Test void administratorCanReadWithoutCaching() throws Exception {
        when(monitor.today()).thenReturn(List.of());
        mvc.perform(get(URL).header("Authorization", "Bearer " + tokens.generateToken(0L, "admin", List.of("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("[]"));
        verify(monitor).today();
    }

    @Test void customerCannotReadAllAccounts() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + tokens.generateToken(1L, "customer", List.of("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(monitor);
    }

    @Test void missingMalformedExpiredAndForgedTokensCannotRead() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        String expired = new JwtTokenProvider(SECRET, -60000).generateToken(0L, "admin", List.of("ROLE_ADMIN"));
        String forged = new JwtTokenProvider("different-signing-key-with-more-than-thirty-two-characters", 60000)
                .generateToken(0L, "admin", List.of("ROLE_ADMIN"));
        for (String token : List.of("bad.token.value", expired, forged)) {
            mvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(monitor);
    }
}
