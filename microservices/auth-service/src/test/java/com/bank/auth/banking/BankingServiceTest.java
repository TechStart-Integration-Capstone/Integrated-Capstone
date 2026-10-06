package com.bank.auth.banking;

import com.bank.auth.config.DataInitializer;
import com.bank.auth.model.Customer;
import com.bank.auth.repository.CustomerRepository;
import com.bank.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankingServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String secret = "01234567890123456789012345678901234567890123456789";
    private final JwtTokenProvider tokens = new JwtTokenProvider(secret, 60000);
    private final BankingLedger ledger = mock(BankingLedger.class);
    private final BankingService service = new BankingService(customers, jdbc, passwords, tokens, ledger);
    private Customer customer;

    @BeforeEach void setUp() {
        customer = mock(Customer.class);
        when(customer.getCustomerId()).thenReturn(42L);
        when(customer.getStatus()).thenReturn("ACTIVE");
        when(customer.getUsername()).thenReturn("new_customer");
        when(customer.getFirstName()).thenReturn("New");
        when(customer.getLastName()).thenReturn("Customer");
        when(customer.getEmail()).thenReturn("new@example.com");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
    }

    private BankingService.Registration registration() {
        return new BankingService.Registration("New", "Customer", "NEW@example.com", "+639171234567", "New_Customer", "different-password");
    }

    @Test void registrationCreatesSavingsAndEverydayWithRecordedWelcomeGift() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
        when(customers.saveAndFlush(any())).thenReturn(customer);
        when(jdbc.queryForObject(contains("SELECT account_id"), eq(Long.class), any())).thenReturn(77L);
        var response = service.register(registration());
        ArgumentCaptor<Customer> stored = ArgumentCaptor.forClass(Customer.class);
        verify(customers).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getUsername()).isEqualTo("new_customer");
        assertThat(stored.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(passwords.matches("different-password", stored.getValue().getPasswordHash())).isTrue();
        ArgumentCaptor<String> savings=ArgumentCaptor.forClass(String.class), everyday=ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("'PHP', ?, 'ACTIVE'"), eq(42L), savings.capture(), eq("SAVINGS_ACCOUNT"), eq(java.math.BigDecimal.ZERO));
        verify(jdbc).update(contains("'PHP', ?, 'ACTIVE'"), eq(42L), everyday.capture(), eq("EVERYDAY_ACCOUNT"), eq(new java.math.BigDecimal("50.00")));
        assertThat(BankingIdentifiers.isAccount(savings.getValue())).isTrue();
        assertThat(BankingIdentifiers.isAccount(everyday.getValue())).isTrue();
        assertThat(savings.getValue().substring(4,11)).isEqualTo(everyday.getValue().substring(4,11));
        assertThat(savings.getValue()).startsWith("0011");
        assertThat(everyday.getValue()).startsWith("0012");
        verify(ledger).record(any(), isNull(), eq(new java.math.BigDecimal("50.00")), eq(new java.math.BigDecimal("50.00")), eq("CREDIT"), eq("WELCOME_GIFT"), eq("WELCOME-42"));
        assertThat(tokens.customerId("Bearer " + response.getToken())).isEqualTo(42L);
    }

    @Test void duplicateRegistrationDoesNotInsertCustomerOrAccount() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
        assertThatThrownBy(() -> service.register(registration())).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        verify(customers, never()).saveAndFlush(any());
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test void concurrentAccountNumberCollisionRetriesBeforeCreatingEverydayAccount() {
        when(customers.saveAndFlush(any())).thenReturn(customer);
        when(jdbc.queryForObject(contains("SELECT account_id"), eq(Long.class), any())).thenReturn(77L);
        when(jdbc.update(contains("INSERT INTO ACCOUNT"),eq(42L),anyString(),eq("SAVINGS_ACCOUNT"),any()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("account_number collision")).thenReturn(1);
        service.register(registration());
        verify(jdbc,times(2)).update(contains("INSERT INTO ACCOUNT"),eq(42L),anyString(),eq("SAVINGS_ACCOUNT"),any());
        verify(jdbc,times(1)).update(contains("INSERT INTO ACCOUNT"),eq(42L),anyString(),eq("EVERYDAY_ACCOUNT"),any());
        verify(ledger,times(1)).record(any(),isNull(),any(),any(),eq("CREDIT"),eq("WELCOME_GIFT"),eq("WELCOME-42"));
    }

    @Test void readsScopeAccountsAndTransactionsToVerifiedCustomer() {
        String token = "Bearer " + tokens.generateToken(42L, "new_customer", List.of("ROLE_CUSTOMER"));
        service.profile(token);
        service.activity(token);
        verify(jdbc).query(contains("WHERE customer_id = ?"), any(RowMapper.class), eq(42L));
        verify(jdbc).query(contains("WHERE a.customer_id = ?"), any(RowMapper.class), eq(42L), eq(42L));
        verify(customers, times(2)).findById(42L);
    }

    @Test void missingForgedAndExpiredTokensCannotReadBankingData() {
        var expired = new JwtTokenProvider(secret, -1000);
        var forged = new JwtTokenProvider("different-key-with-at-least-thirty-two-bytes-long", 60000);
        for (String token : Arrays.asList(null, "Bearer invalid", "Bearer " + expired.generateToken(42L,"new_customer",List.of()),
                "Bearer " + forged.generateToken(42L,"new_customer",List.of()))) {
            assertThatThrownBy(() -> service.profile(token)).isInstanceOfSatisfying(ResponseStatusException.class,
                    ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401));
        }
        verifyNoInteractions(jdbc);
    }

    @Test void inactiveCustomerCannotReadBankingData() {
        when(customer.getStatus()).thenReturn("CLOSED");
        assertThatThrownBy(() -> service.profile("Bearer " + tokens.generateToken(42L,"new_customer",List.of())))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void startupNeverResetsRegisteredCustomersPasswords() {
        Customer registered = new Customer("new_customer",passwords.encode("unique-personal-password"),"New","Customer","new@example.com","12345678");
        String original = registered.getPasswordHash();
        when(customers.findAll()).thenReturn(List.of(registered));
        new DataInitializer(customers,passwords).run();
        assertThat(registered.getPasswordHash()).isEqualTo(original);
        verify(customers,never()).save(any());
    }
}
