package com.bank.account.controller;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.CustomerDto;
import com.bank.account.service.AccountService;
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

    public AccountController(AccountService accountService) { this.accountService = accountService; }

    @GetMapping("/me")
    public ResponseEntity<com.bank.account.dto.UserProfileDto> getMe(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam) {

        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        if (customerId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
        return ResponseEntity.ok(accountService.getUserProfile(customerId));
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

    @GetMapping("/customers")
    public ResponseEntity<List<CustomerDto>> getAllCustomers() {
        return ResponseEntity.ok(accountService.getAllCustomers());
    }

    @PostMapping("/{accountId}/status")
    public ResponseEntity<AccountDto> updateAccountStatus(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
                                                          @PathVariable Long accountId,
                                                          @RequestBody Map<String, String> payload) {
        assertAdmin(roles);
        String status = payload.getOrDefault("status", "ACTIVE");
        return ResponseEntity.ok(accountService.updateAccountStatus(accountId, status));
    }

    @PostMapping("/customer/{customerId}/status")
    public ResponseEntity<CustomerDto> updateCustomerStatus(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
                                                            @PathVariable Long customerId,
                                                            @RequestBody Map<String, String> payload) {
        assertAdmin(roles);
        String status = payload.getOrDefault("status", "ACTIVE");
        return ResponseEntity.ok(accountService.updateCustomerStatus(customerId, status));
    }

    @PostMapping("/{accountId}/reset-balance")
    public ResponseEntity<AccountDto> resetBalance(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
                                                   @PathVariable Long accountId,
                                                   @RequestBody Map<String, BigDecimal> payload) {
        assertAdmin(roles);
        BigDecimal targetBalance = payload.getOrDefault("targetBalance", new BigDecimal("60.0000"));
        return ResponseEntity.ok(accountService.resetAccountBalance(accountId, targetBalance));
    }

    private void assertAdmin(String roles) {
        if (roles == null || java.util.Arrays.stream(roles.split(",")).map(String::trim).noneMatch("ROLE_ADMIN"::equals)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Admin role required");
        }
    }
}
