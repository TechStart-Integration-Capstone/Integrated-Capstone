package com.bank.auth.banking;

import com.bank.auth.model.Customer;
import com.bank.auth.repository.CustomerRepository;
import com.bank.auth.security.JwtTokenProvider;
import com.bank.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BankingMpinTest {
    private JdbcTemplate jdbc;
    private CustomerRepository customers;
    private MockMvc mvc;
    private Customer customer;
    private static final String OLD_PIN = "123456", NEW_PIN = "246810";
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:mpin-" + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc = spy(new JdbcTemplate(source));
        jdbc.execute("CREATE TABLE CUSTOMER(customer_id BIGINT PRIMARY KEY, mpin_hash VARCHAR(255))");
        jdbc.update("INSERT INTO CUSTOMER VALUES(42,?),(99,?)", BankingService.hashMpin(OLD_PIN), BankingService.hashMpin("987654"));
        customers = mock(CustomerRepository.class);
        customer = mock(Customer.class);
        when(customer.getCustomerId()).thenReturn(42L);
        when(customer.getStatus()).thenReturn("ACTIVE");
        // The entity deliberately lacks a hash; the database must still protect an existing PIN.
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        var tokens = mock(JwtTokenProvider.class);
        when(tokens.customerId("Bearer test")).thenReturn(42L);
        var banking = new BankingService(customers, jdbc, mock(PasswordEncoder.class), tokens, mock(BankingLedger.class));
        mvc = MockMvcBuilders.standaloneSetup(new BankingController(banking, mock(AuthService.class),
                mock(BankingTransferService.class), mock(BankingRecipientService.class))).build();
    }

    private String body(String field, String current) throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put(field, NEW_PIN);
        if (current != null) request.put("currentMpin", current);
        return json.writeValueAsString(request);
    }

    private String stored() {
        return jdbc.queryForObject("SELECT mpin_hash FROM CUSTOMER WHERE customer_id=42", String.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "654321", "12345", "abcdef"})
    void existingPinRejectsMissingBlankIncorrectOrMalformedCurrentPin(String current) throws Exception {
        mvc.perform(post("/api/v1/auth/banking/mpin").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body("mpin", current)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(stored()).isEqualTo(BankingService.hashMpin(OLD_PIN));
        verify(customers, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mpin", "pin"})
    void correctCurrentPinChangesOnlyAuthenticatedCustomerAndStillVerifies(String field) throws Exception {
        mvc.perform(post("/api/v1/auth/banking/mpin").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body(field, OLD_PIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(stored()).isEqualTo(BankingService.hashMpin(NEW_PIN));
        assertThat(jdbc.queryForObject("SELECT mpin_hash FROM CUSTOMER WHERE customer_id=99", String.class))
                .isEqualTo(BankingService.hashMpin("987654"));
        mvc.perform(post("/api/v1/auth/banking/mpin/verify").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body(field, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void firstTimeSetupDoesNotRequireCurrentPin(String storedHash) throws Exception {
        jdbc.update("UPDATE CUSTOMER SET mpin_hash=? WHERE customer_id=42", storedHash);
        mvc.perform(post("/api/v1/auth/banking/mpin").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body("mpin", null)))
                .andExpect(status().isOk());
        assertThat(stored()).isEqualTo(BankingService.hashMpin(NEW_PIN));
    }

    @Test void failedAuthoritativeReadCannotBeTreatedAsFirstTimeSetup() throws Exception {
        doThrow(new DataAccessResourceFailureException("unavailable")).when(jdbc)
                .queryForObject("SELECT mpin_hash FROM CUSTOMER WHERE customer_id = ?", String.class, 42L);
        mvc.perform(post("/api/v1/auth/banking/mpin").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body("mpin", null)))
                .andExpect(status().isServiceUnavailable());
        assertThat(stored()).isEqualTo(BankingService.hashMpin(OLD_PIN));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentChangeOrSetupCannotOverwriteTheWinner(boolean firstSetup) throws Exception {
        if (firstSetup) jdbc.update("UPDATE CUSTOMER SET mpin_hash=NULL WHERE customer_id=42");
        var actual = new JdbcTemplate(jdbc.getDataSource());
        doAnswer(call -> {
            actual.update("UPDATE CUSTOMER SET mpin_hash=? WHERE customer_id=42", BankingService.hashMpin("111222"));
            return call.callRealMethod();
        }).when(jdbc).update(startsWith("UPDATE CUSTOMER SET mpin_hash = ? WHERE customer_id = ? AND"),
                any(Object[].class));
        mvc.perform(post("/api/v1/auth/banking/mpin").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body("mpin", firstSetup ? null : OLD_PIN)))
                .andExpect(status().isConflict());
        assertThat(stored()).isEqualTo(BankingService.hashMpin("111222"));
    }

    @Test void failedWriteDoesNotReportSuccess() throws Exception {
        doThrow(new DataAccessResourceFailureException("unavailable")).when(jdbc)
                .update(startsWith("UPDATE CUSTOMER SET mpin_hash = ? WHERE customer_id = ? AND"), any(Object[].class));
        mvc.perform(post("/api/v1/auth/banking/mpin").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body("mpin", OLD_PIN)))
                .andExpect(status().isServiceUnavailable());
        assertThat(stored()).isEqualTo(BankingService.hashMpin(OLD_PIN));
    }

    @Test void staleEntityHashCannotAuthorizeAnOldPin() throws Exception {
        when(customer.getMpinHash()).thenReturn(BankingService.hashMpin("654321"));
        mvc.perform(post("/api/v1/auth/banking/mpin").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body("mpin", "654321")))
                .andExpect(status().isBadRequest());
        assertThat(stored()).isEqualTo(BankingService.hashMpin(OLD_PIN));
    }
}
