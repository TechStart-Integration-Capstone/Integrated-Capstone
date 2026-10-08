package com.bank.t24.controller;

import com.bank.t24.dto.T24AccountBalanceResponse;
import com.bank.t24.model.Account;
import com.bank.t24.repository.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Authoritative Core Account Inquiry Endpoint for T24 Core Banking (Phase 5).
 */
@Tag(name = "T24 Account Inquiry", description = "Authoritative core account balance inquiry from T24 Core Banking System of Record")
@RestController
@RequestMapping("/api/v1/t24/accounts")
public class T24AccountInquiryController {

    private final AccountRepository accountRepository;

    public T24AccountInquiryController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Operation(summary = "Get core account balance", description = "Retrieves authoritative real-time current, held, and available balances directly from T24 Core.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authoritative balance details returned"),
            @ApiResponse(responseCode = "404", description = "Account not found in T24 Core")
    })
    @GetMapping("/{accountIdOrNumber}/balance")
    public ResponseEntity<T24AccountBalanceResponse> getAccountBalance(
            @Parameter(description = "Account ID or Account Number", example = "ACC-1001")
            @PathVariable String accountIdOrNumber) {
        Account account = null;
        try {
            Long accountId = Long.parseLong(accountIdOrNumber);
            account = accountRepository.findById(accountId).orElse(null);
        } catch (NumberFormatException ignored) {}

        if (account == null) {
            account = accountRepository.findByAccountNumber(accountIdOrNumber).orElse(null);
        }

        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found in T24 Core: " + accountIdOrNumber);
        }

        BigDecimal current = account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO;
        BigDecimal held = account.getHeldBalance() != null ? account.getHeldBalance() : BigDecimal.ZERO;
        BigDecimal available = current.subtract(held);

        return ResponseEntity.ok(new T24AccountBalanceResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getCurrency(),
                current,
                held,
                available,
                account.getStatus()
        ));
    }
}
