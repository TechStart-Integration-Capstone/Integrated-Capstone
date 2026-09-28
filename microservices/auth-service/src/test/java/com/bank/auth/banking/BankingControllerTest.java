package com.bank.auth.banking;

import com.bank.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BankingControllerTest {
    private final BankingService banking = mock(BankingService.class);
    private final AuthService auth = mock(AuthService.class);
    private final BankingTransferService transfers = mock(BankingTransferService.class);
    private MockMvc mvc;

    @BeforeEach void setUp() { mvc = MockMvcBuilders.standaloneSetup(new BankingController(banking,auth,transfers,mock(BankingRecipientService.class))).build(); }

    @Test void invalidTransfersAreRejectedBeforeWriting() throws Exception {
        for (String amount : new String[]{"0", "-1", "0.001", "100000000000000"}) {
            mvc.perform(post("/api/v1/auth/banking/transfers").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sourceAccountId\":1,\"destinationAccountNumber\":\"PP-TEST\",\"amount\":" + amount
                            + ",\"idempotencyKey\":\"1234567890123456\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(transfers);
    }

    @Test void invalidRegistrationIsRejectedBeforeWriting() throws Exception {
        mvc.perform(post("/api/v1/auth/banking/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":" ","lastName":"Customer","email":"not-an-email","phone":"x","username":"!","password":"short"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(banking);
    }

    @Test void incorrectLoginReturnsHelpfulUnauthorizedResponse() throws Exception {
        when(auth.authenticate(any())).thenThrow(new RuntimeException("Invalid username or password"));
        mvc.perform(post("/api/v1/auth/banking/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new_customer\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Your username or password is incorrect."));
    }
}
