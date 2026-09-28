package com.bank.transaction.controller;

import com.bank.transaction.dto.MutationRequest;
import com.bank.transaction.dto.MutationResponse;
import com.bank.transaction.service.LedgerMutationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ledger")
@CrossOrigin(origins = "*")
public class LedgerMutationController {

    private final LedgerMutationService ledgerMutationService;

    public LedgerMutationController(LedgerMutationService ledgerMutationService) {
        this.ledgerMutationService = ledgerMutationService;
    }

    @PostMapping("/mutate")
    public ResponseEntity<MutationResponse> mutate(
            @Valid @RequestBody MutationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Auth-Username", required = false) String username) {

        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request.setIdempotencyKey(idempotencyKeyHeader);
        }
        String user = (username != null && !username.isBlank()) ? username : "anonymous";
        return ResponseEntity.ok(ledgerMutationService.mutateBalance(request, user));
    }
}
