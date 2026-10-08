package com.bank.account.controller;

import com.bank.account.dto.RecipientDto;
import com.bank.account.service.BeneficiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Recipient & Favorites Controller for account-service (Phase 5).
 * Consolidates directory, lookup, and favorites operations.
 */
@Tag(name = "Beneficiaries & Recipients", description = "Recipient directory lookup, favorite accounts management, and transfer recipient validation.")
@RestController
@RequestMapping("/api/v1/accounts")
@CrossOrigin(origins = "*")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    public record FavoriteRequest(@NotBlank String accountNumber) {}

    @Operation(summary = "Lookup Recipient by Account Number", description = "Looks up an account by its 12-digit number to verify account existence and whether it is marked as a favourite.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recipient account details found"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/recipients/lookup")
    public ResponseEntity<RecipientDto> lookup(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Admin override: customer ID", example = "2")
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader,
            @Parameter(description = "12-digit account number to lookup", example = "001181233470")
            @RequestParam String accountNumber) {

        Long customerId = resolveCustomerId(customerIdHeader, customerIdParam, rolesHeader);
        return ResponseEntity.ok(beneficiaryService.lookup(customerId, accountNumber));
    }

    public ResponseEntity<RecipientDto> lookup(Long customerIdHeader, Long customerIdParam, String accountNumber) {
        return lookup(customerIdHeader, customerIdParam, null, accountNumber);
    }

    @Operation(summary = "Get Recipients Directory", description = "Returns the customer's transfer directory, listing saved favourites and recent transfer counterparties.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Directory returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/recipients")
    public ResponseEntity<RecipientDto.DirectoryDto> directory(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Admin override: customer ID", example = "2")
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader) {

        Long customerId = resolveCustomerId(customerIdHeader, customerIdParam, rolesHeader);
        return ResponseEntity.ok(beneficiaryService.directory(customerId));
    }

    public ResponseEntity<RecipientDto.DirectoryDto> directory(Long customerIdHeader, Long customerIdParam) {
        return directory(customerIdHeader, customerIdParam, null);
    }

    @Operation(summary = "Save Favourite Beneficiary", description = "Saves an account number to the customer's favourite beneficiaries list.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Favourite saved successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Account number does not exist")
    })
    @PostMapping("/favorites")
    public ResponseEntity<RecipientDto> saveFavorite(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Admin override: customer ID", example = "2")
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader,
            @Valid @RequestBody FavoriteRequest request) {

        Long customerId = resolveCustomerId(customerIdHeader, customerIdParam, rolesHeader);
        return ResponseEntity.ok(beneficiaryService.saveFavorite(customerId, request.accountNumber()));
    }

    public ResponseEntity<RecipientDto> saveFavorite(Long customerIdHeader, Long customerIdParam, FavoriteRequest request) {
        return saveFavorite(customerIdHeader, customerIdParam, null, request);
    }

    @Operation(summary = "Remove Favourite Beneficiary", description = "Removes an account number from the customer's favourite beneficiaries list.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Favourite removed"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @DeleteMapping("/favorites/{accountNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Admin override: customer ID", example = "2")
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader,
            @Parameter(description = "12-digit account number to remove", example = "001181233470")
            @PathVariable String accountNumber) {

        Long customerId = resolveCustomerId(customerIdHeader, customerIdParam, rolesHeader);
        beneficiaryService.removeFavorite(customerId, accountNumber);
    }

    public void removeFavorite(Long customerIdHeader, Long customerIdParam, String accountNumber) {
        removeFavorite(customerIdHeader, customerIdParam, null, accountNumber);
    }

    private Long resolveCustomerId(Long customerIdHeader, Long customerIdParam, String rolesHeader) {
        if (rolesHeader != null && java.util.Arrays.stream(rolesHeader.split(",")).map(String::trim).anyMatch("ROLE_ADMIN"::equals) && customerIdParam != null) {
            return customerIdParam;
        }
        if (customerIdHeader != null) {
            return customerIdHeader;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing customer identity.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Request failed."));
    }
}
