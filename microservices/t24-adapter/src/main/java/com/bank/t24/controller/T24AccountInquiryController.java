package com.bank.t24.controller;

import com.bank.t24.dto.T24AccountBalanceResponse;
import com.bank.t24.model.Account;
import com.bank.t24.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Authoritative Core Account Inquiry Endpoint for T24 Core Banking (Phase 5).
 */
@RestController
@RequestMapping("/api/v1/t24/accounts")
public class T24AccountInquiryController {

    private final AccountRepository accountRepository;

    public T24AccountInquiryController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/{accountIdOrNumber}/balance")
    public ResponseEntity<T24AccountBalanceResponse> getAccountBalance(@PathVariable String accountIdOrNumber) {
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
