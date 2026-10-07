package com.bank.account.controller;

import com.bank.account.dto.AccountDto;
import com.bank.account.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private final AccountController controller = new AccountController(accountService);

    @Test
    @DisplayName("resetBalance without ROLE_ADMIN throws 403 Forbidden")
    void resetBalance_withoutAdmin_throws403() {
        assertThatThrownBy(() -> controller.resetBalance("ROLE_CUSTOMER", 1L, Map.of("targetBalance", BigDecimal.TEN)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> controller.resetBalance(null, 1L, Map.of("targetBalance", BigDecimal.TEN)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(accountService, never()).resetAccountBalance(anyLong(), any());
    }

    @Test
    @DisplayName("resetBalance with ROLE_ADMIN succeeds")
    void resetBalance_withAdmin_succeeds() {
        AccountDto dto = new AccountDto(1L, 1L, "001100000001", "SAVINGS", "PHP", BigDecimal.TEN, "ACTIVE", java.time.LocalDateTime.now());
        when(accountService.resetAccountBalance(1L, BigDecimal.TEN)).thenReturn(dto);

        ResponseEntity<AccountDto> response = controller.resetBalance("ROLE_ADMIN", 1L, Map.of("targetBalance", BigDecimal.TEN));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCurrentBalance()).isEqualTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("updateAccountStatus without ROLE_ADMIN throws 403 Forbidden")
    void updateAccountStatus_withoutAdmin_throws403() {
        assertThatThrownBy(() -> controller.updateAccountStatus("ROLE_CUSTOMER", 1L, Map.of("status", "FROZEN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(accountService, never()).updateAccountStatus(anyLong(), anyString());
    }
}
