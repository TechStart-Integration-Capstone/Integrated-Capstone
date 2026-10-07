package com.bank.account.controller;

import com.bank.account.dto.RecipientDto;
import com.bank.account.service.BeneficiaryService;
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
@RestController
@RequestMapping("/api/v1/accounts")
@CrossOrigin(origins = "*")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    public record FavoriteRequest(@NotBlank String accountNumber) {}

    @GetMapping("/recipients/lookup")
    public ResponseEntity<RecipientDto> lookup(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @RequestParam String accountNumber) {

        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        return ResponseEntity.ok(beneficiaryService.lookup(customerId, accountNumber));
    }

    @GetMapping("/recipients")
    public ResponseEntity<RecipientDto.DirectoryDto> directory(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam) {

        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        return ResponseEntity.ok(beneficiaryService.directory(customerId));
    }

    @PostMapping("/favorites")
    public ResponseEntity<RecipientDto> saveFavorite(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Valid @RequestBody FavoriteRequest request) {

        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        return ResponseEntity.ok(beneficiaryService.saveFavorite(customerId, request.accountNumber()));
    }

    @DeleteMapping("/favorites/{accountNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @PathVariable String accountNumber) {

        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        beneficiaryService.removeFavorite(customerId, accountNumber);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Request failed."));
    }
}
