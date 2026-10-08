package com.bank.transaction.controller;

import com.bank.transaction.dto.MutationRequest;
import com.bank.transaction.dto.MutationResponse;
import com.bank.transaction.service.LedgerMutationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Direct Ledger Mutation", description = "Direct balance mutation service for adjustments and cash-in operations")
@RestController
@RequestMapping("/api/v1/ledger")
@CrossOrigin(origins = "*")
public class LedgerMutationController {

    private final LedgerMutationService ledgerMutationService;

    public LedgerMutationController(LedgerMutationService ledgerMutationService) {
        this.ledgerMutationService = ledgerMutationService;
    }

    @Operation(summary = "Mutate account balance", description = "Applies a direct debit or credit balance mutation against an account with row-level pessimistic locking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance mutation applied successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid mutation parameters"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds or account inactive")
    })
    @PostMapping("/mutate")
    public ResponseEntity<MutationResponse> mutate(
            @Valid @RequestBody MutationRequest request,
            @Parameter(description = "Idempotency key for mutation", example = "mut-idemp-12345")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Parameter(description = "Username executing the mutation", example = "admin")
            @RequestHeader(value = "X-Auth-Username", required = false) String username) {

        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request.setIdempotencyKey(idempotencyKeyHeader);
        }
        String user = (username != null && !username.isBlank()) ? username : "anonymous";
        return ResponseEntity.ok(ledgerMutationService.mutateBalance(request, user));
    }
}
