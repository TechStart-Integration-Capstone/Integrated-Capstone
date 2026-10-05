package com.bank.transaction.controller;

import com.bank.transaction.dto.InternalTransferRequest;
import com.bank.transaction.dto.InternalTransferResponse;
import com.bank.transaction.service.RemittanceOrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Internal money-movement endpoint for loan-service (Phase 6 Loans).
 *
 * Lives under /internal/** on purpose: the API Gateway has no route for it, so only services on the
 * Docker network can reach it. Callers must also identify themselves with X-Internal-Service.
 */
@RestController
@RequestMapping("/internal/remittance")
public class InternalTransferController {

    private static final Logger log = LoggerFactory.getLogger(InternalTransferController.class);
    static final String ALLOWED_CALLER = "loan-service";

    private final RemittanceOrchestratorService orchestratorService;

    public InternalTransferController(RemittanceOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<InternalTransferResponse> transfer(
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody InternalTransferRequest request) {

        if (!ALLOWED_CALLER.equals(internalService)) {
            log.warn("[internal-transfer] Rejected caller X-Internal-Service={}", internalService);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal endpoint: caller not allowed");
        }

        log.info("[internal-transfer] {} {} -> {} amount={} key={}", request.getTransactionType(),
                request.getSourceAccountNo(), request.getTargetAccountNo(), request.getAmount(), request.getIdempotencyKey());

        return ResponseEntity.ok(orchestratorService.processInternalTransfer(request, correlationId));
    }
}
