package com.bank.ledger.controller;

import com.bank.ledger.dto.MutationRequest;
import com.bank.ledger.dto.MutationResponse;
import com.bank.ledger.security.IdempotencyHandlerInterceptor;
import com.bank.ledger.service.LedgerMutationFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ledger")
@CrossOrigin(origins = "*")
public class LedgerMutationController {

    private final LedgerMutationFacade ledgerMutationFacade;

    public LedgerMutationController(LedgerMutationFacade ledgerMutationFacade) {
        this.ledgerMutationFacade = ledgerMutationFacade;
    }

    /**
     * Requirement 1.A: Strictly enforces JSR-380 validation (@Digits(14,4), @Positive).
     * Intercepted by GlobalExceptionHandler on violation.
     * The Idempotency-Key header is resolved exactly the same way as in IdempotencyHandlerInterceptor.
     */
    @PostMapping("/mutate")
    public ResponseEntity<MutationResponse> mutateLedger(
            @Valid @RequestBody MutationRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        String idempotencyKey = IdempotencyHandlerInterceptor.resolveKey(httpRequest);
        if (idempotencyKey != null) {
            request.setIdempotencyKey(idempotencyKey);
        }

        String username = authentication != null ? authentication.getName() : "jdelacruz";
        MutationResponse response = ledgerMutationFacade.executeMutation(request, username);
        return ResponseEntity.ok(response);
    }
}
