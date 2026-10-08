package com.bank.t24.controller;

import com.bank.t24.model.Account;
import com.bank.t24.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class T24AccountInquiryControllerTest {

    private AccountRepository accountRepository;
    private T24AccountInquiryController controller;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        controller = new T24AccountInquiryController(accountRepository);
    }

    @Test
    @DisplayName("getAccountBalance returns balance by account number")
    void getAccountBalance_byAccountNumber_success() {
        Account acc = new Account();
        acc.setAccountId(10L);
        acc.setAccountNumber("001181233469");
        acc.setAccountType("SAVINGS_ACCOUNT");
        acc.setCurrency("PHP");
        acc.setCurrentBalance(new BigDecimal("10000.0000"));
        acc.setHeldBalance(new BigDecimal("1500.0000"));
        acc.setStatus("ACTIVE");
        acc.setCreatedDate(LocalDateTime.now());

        when(accountRepository.findByAccountNumber("001181233469")).thenReturn(Optional.of(acc));

        var response = controller.getAccountBalance("001181233469");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currentBalance()).isEqualByComparingTo("10000.0000");
        assertThat(response.getBody().heldBalance()).isEqualByComparingTo("1500.0000");
        assertThat(response.getBody().availableBalance()).isEqualByComparingTo("8500.0000");
    }

    @Test
    @DisplayName("getAccountBalance throws 404 when account does not exist")
    void getAccountBalance_notFound() {
        when(accountRepository.findByAccountNumber("NON_EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getAccountBalance("NON_EXISTENT"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Account not found in T24 Core");
    }
}
