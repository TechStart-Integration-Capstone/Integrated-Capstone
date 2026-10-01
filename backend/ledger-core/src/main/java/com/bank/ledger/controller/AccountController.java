package com.bank.ledger.controller;

import com.bank.ledger.dto.AccountDto;
import com.bank.ledger.dto.CustomerDto;
import com.bank.ledger.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<AccountDto>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getAccountById(accountId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CustomerDto> getCustomerProfile(@PathVariable Long customerId) {
        return ResponseEntity.ok(accountService.getCustomerProfile(customerId));
    }

    @PostMapping("/{accountId}/reset-balance")
    public ResponseEntity<AccountDto> resetBalance(
            @PathVariable Long accountId,
            @RequestBody Map<String, BigDecimal> payload) {
        BigDecimal targetBalance = payload.getOrDefault("targetBalance", new BigDecimal("60.0000"));
        return ResponseEntity.ok(accountService.resetAccountBalance(accountId, targetBalance));
    }
}
