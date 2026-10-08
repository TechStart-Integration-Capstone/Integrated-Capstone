package com.bank.account.controller;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.CustomerDto;
import com.bank.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Accounts & Profiles", description = "Customer account balance inquiry, profile details, and administrative account lifecycle management.")
@RestController
@RequestMapping("/api/v1/accounts")
@CrossOrigin(origins = "*")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) { this.accountService = accountService; }

    @Operation(summary = "Get Caller Profile & Accounts", description = "Retrieves live profile, personal details, and all owned accounts with current balances for the authenticated caller.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile and accounts retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid customer identity")
    })
    @GetMapping("/me")
    public ResponseEntity<com.bank.account.dto.UserProfileDto> getMe(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Admin override: look up specific customer ID", example = "2")
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {

        Long customerId = customerIdHeader;
        if (isAdmin(roles) && customerIdParam != null) {
            customerId = customerIdParam;
        }
        if (customerId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
        return ResponseEntity.ok(accountService.getUserProfile(customerId));
    }

    public ResponseEntity<com.bank.account.dto.UserProfileDto> getMe(Long customerIdHeader, Long customerIdParam) {
        return getMe(customerIdHeader, customerIdParam, null);
    }

    @Operation(summary = "List Bank Accounts", description = "Lists accounts. When called by a regular customer, returns only their own accounts. When called by an administrator, returns all bank accounts. Supports optional ?page=&size= pagination.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of bank accounts"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping
    public ResponseEntity<List<AccountDto>> getAllAccounts(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size (records per page)", example = "10")
            @RequestParam(value = "size", required = false) Integer size) {
        List<AccountDto> accounts;
        if (isAdmin(roles)) {
            accounts = accountService.getAllAccounts();
        } else if (customerIdHeader != null) {
            accounts = accountService.getAccountsByCustomerId(customerIdHeader);
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
        return ResponseEntity.ok(paginate(accounts, page, size));
    }

    public ResponseEntity<List<AccountDto>> getAllAccounts(String roles, Long customerIdHeader) {
        return getAllAccounts(roles, customerIdHeader, null, null);
    }

    public ResponseEntity<List<AccountDto>> getAllAccounts() {
        return getAllAccounts(null, null, null, null);
    }

    @Operation(summary = "Get Account by ID", description = "Retrieves an account by internal account ID. Customers can only retrieve their own accounts; administrators can inspect any account.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account found and returned"),
        @ApiResponse(responseCode = "403", description = "Caller does not own this account"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDto> getAccount(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Account ID", example = "1")
            @PathVariable Long accountId) {
        AccountDto account = accountService.getAccountById(accountId);
        if (!isAdmin(roles)) {
            if (customerIdHeader == null || !customerIdHeader.equals(account.getCustomerId())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Access denied to account " + accountId);
            }
        }
        return ResponseEntity.ok(account);
    }

    public ResponseEntity<AccountDto> getAccount(Long accountId) {
        return ResponseEntity.ok(accountService.getAccountById(accountId));
    }

    @Operation(summary = "Get Customer Profile by ID", description = "Retrieves a customer profile by customer ID. Non-admin callers can only access their own profile.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer profile returned"),
        @ApiResponse(responseCode = "403", description = "Forbidden: Cannot access another customer's profile"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CustomerDto> getCustomerProfile(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Customer ID", example = "2")
            @PathVariable Long customerId) {
        if (!isAdmin(roles)) {
            if (customerIdHeader == null || !customerIdHeader.equals(customerId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Access denied to customer profile " + customerId);
            }
        }
        return ResponseEntity.ok(accountService.getCustomerProfile(customerId));
    }

    public ResponseEntity<CustomerDto> getCustomerProfile(Long customerId) {
        return ResponseEntity.ok(accountService.getCustomerProfile(customerId));
    }

    @Operation(summary = "List All Customers [Admin Only 👑]", description = "Retrieves the full list of registered banking customers across the platform. Requires ROLE_ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customers list returned"),
        @ApiResponse(responseCode = "403", description = "Forbidden: Administrator privileges required")
    })
    @GetMapping("/customers")
    public ResponseEntity<List<CustomerDto>> getAllCustomers(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size (records per page)", example = "10")
            @RequestParam(value = "size", required = false) Integer size) {
        assertAdmin(roles);
        return ResponseEntity.ok(paginate(accountService.getAllCustomers(), page, size));
    }

    public ResponseEntity<List<CustomerDto>> getAllCustomers(String roles) {
        return getAllCustomers(roles, null, null);
    }

    public ResponseEntity<List<CustomerDto>> getAllCustomers() {
        return getAllCustomers(null, null, null);
    }

    @Operation(summary = "Update Account Lifecycle Status [Admin Only 👑]", description = "Changes an account lifecycle status (ACTIVE, FROZEN, CLOSED). Requires ROLE_ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "403", description = "Forbidden: Administrator privileges required")
    })
    @PostMapping("/{accountId}/status")
    public ResponseEntity<AccountDto> updateAccountStatus(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Account ID", example = "1")
            @PathVariable Long accountId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Target status payload e.g. {\"status\":\"FROZEN\"}")
            @RequestBody Map<String, String> payload) {
        assertAdmin(roles);
        String status = payload.getOrDefault("status", "ACTIVE");
        return ResponseEntity.ok(accountService.updateAccountStatus(accountId, status));
    }

    @Operation(summary = "Update Customer Status [Admin Only 👑]", description = "Changes a customer account status (ACTIVE, LOCKED, INACTIVE). Requires ROLE_ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer status updated"),
        @ApiResponse(responseCode = "403", description = "Forbidden: Administrator privileges required")
    })
    @PostMapping("/customer/{customerId}/status")
    public ResponseEntity<CustomerDto> updateCustomerStatus(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Customer ID", example = "2")
            @PathVariable Long customerId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Target status payload e.g. {\"status\":\"LOCKED\"}")
            @RequestBody Map<String, String> payload) {
        assertAdmin(roles);
        String status = payload.getOrDefault("status", "ACTIVE");
        return ResponseEntity.ok(accountService.updateCustomerStatus(customerId, status));
    }

    @Operation(summary = "Reset Account Balance [Admin / Test Only 👑]", description = "Resets an account balance to a specified test amount (default ₱60.00). Used for idempotent automated testing. Requires ROLE_ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance reset successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden: Administrator privileges required")
    })
    @PostMapping("/{accountId}/reset-balance")
    public ResponseEntity<AccountDto> resetBalance(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Account ID", example = "1")
            @PathVariable Long accountId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Target balance e.g. {\"targetBalance\": 60.00}")
            @RequestBody Map<String, BigDecimal> payload) {
        assertAdmin(roles);
        BigDecimal targetBalance = payload.getOrDefault("targetBalance", new BigDecimal("60.0000"));
        return ResponseEntity.ok(accountService.resetAccountBalance(accountId, targetBalance));
    }

    private static <T> List<T> paginate(List<T> items, Integer page, Integer size) {
        if (page == null || size == null || size <= 0) {
            return items;
        }
        int p = Math.max(0, page);
        int from = p * size;
        if (from >= items.size()) {
            return List.of();
        }
        int to = Math.min(from + size, items.size());
        return items.subList(from, to);
    }

    private boolean isAdmin(String roles) {
        return roles != null && java.util.Arrays.stream(roles.split(",")).map(String::trim).anyMatch("ROLE_ADMIN"::equals);
    }

    private void assertAdmin(String roles) {
        if (!isAdmin(roles)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Admin role required");
        }
    }
}
