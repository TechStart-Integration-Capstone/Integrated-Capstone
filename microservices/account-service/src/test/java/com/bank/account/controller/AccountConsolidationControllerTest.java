package com.bank.account.controller;

import com.bank.account.dto.RecipientDto;
import com.bank.account.dto.UserProfileDto;
import com.bank.account.service.AccountService;
import com.bank.account.service.BeneficiaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountConsolidationControllerTest {

    private AccountService accountService;
    private BeneficiaryService beneficiaryService;

    private AccountController accountController;
    private BeneficiaryController beneficiaryController;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        beneficiaryService = mock(BeneficiaryService.class);

        accountController = new AccountController(accountService);
        beneficiaryController = new BeneficiaryController(beneficiaryService);
    }

    @Test
    @DisplayName("getMe returns user profile with accounts when authenticated")
    void getMe_success() {
        UserProfileDto.AccountSummaryDto acc = new UserProfileDto.AccountSummaryDto(
                1L, "001181233469", "SAVINGS_ACCOUNT", "PHP", new BigDecimal("5000.00"), "ACTIVE"
        );
        UserProfileDto profile = new UserProfileDto("Juan", "Juan Dela Cruz", "jdelacruz", "juan@paypink.ph", List.of(acc));
        when(accountService.getUserProfile(10L)).thenReturn(profile);

        ResponseEntity<UserProfileDto> response = accountController.getMe(10L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("jdelacruz");
        assertThat(response.getBody().accounts()).hasSize(1);
    }

    @Test
    @DisplayName("getMe throws 401 when customer identity is missing")
    void getMe_unauthorized() {
        assertThatThrownBy(() -> accountController.getMe(null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Please log in again");
    }

    @Test
    @DisplayName("lookup recipient succeeds")
    void lookup_success() {
        RecipientDto recipient = new RecipientDto("001181233469", "Juan Dela Cruz", false);
        when(beneficiaryService.lookup(10L, "001181233469")).thenReturn(recipient);

        ResponseEntity<RecipientDto> response = beneficiaryController.lookup(10L, null, "001181233469");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accountNumber()).isEqualTo("001181233469");
    }

    @Test
    @DisplayName("directory returns favorites and recent recipients")
    void directory_success() {
        RecipientDto r = new RecipientDto("001181233469", "Juan Dela Cruz", true);
        RecipientDto.DirectoryDto dir = new RecipientDto.DirectoryDto(List.of(r), List.of());
        when(beneficiaryService.directory(10L)).thenReturn(dir);

        ResponseEntity<RecipientDto.DirectoryDto> response = beneficiaryController.directory(10L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().favorites()).hasSize(1);
    }

    @Test
    @DisplayName("saveFavorite adds new favorite recipient")
    void saveFavorite_success() {
        RecipientDto r = new RecipientDto("001181233469", "Juan Dela Cruz", true);
        when(beneficiaryService.saveFavorite(10L, "001181233469")).thenReturn(r);

        ResponseEntity<RecipientDto> response = beneficiaryController.saveFavorite(
                10L, null, new BeneficiaryController.FavoriteRequest("001181233469"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().favorite()).isTrue();
    }
}
