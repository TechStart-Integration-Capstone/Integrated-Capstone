package com.bank.transaction.controller;

import com.bank.transaction.dto.InternalTransferRequest;
import com.bank.transaction.dto.InternalTransferResponse;
import com.bank.transaction.service.RemittanceOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Internal Service Remittance", description = "Internal service-to-service money transfer endpoint for loan disbursements and repayments")
@RestController
@RequestMapping("/internal/remittance")
public class InternalTransferController {

    private static final Logger log = LoggerFactory.getLogger(InternalTransferController.class);
    static final String ALLOWED_CALLER = "loan-service";

    private final RemittanceOrchestratorService orchestratorService;

    public InternalTransferController(RemittanceOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @Operation(summary = "Execute internal money transfer", description = "Service-to-service transfer endpoint used exclusively by loan-service for disbursements and repayments. Not routed via public API gateway. Secured by X-Internal-Service header.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Internal transfer processed and settled"),
            @ApiResponse(responseCode = "403", description = "Caller not allowed - must be loan-service"),
            @ApiResponse(responseCode = "422", description = "Transfer rejected by Core Banking")
    })
    @PostMapping("/transfer")
    public ResponseEntity<InternalTransferResponse> transfer(
            @Parameter(description = "Calling microservice name identifier", example = "loan-service")
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService,
            @Parameter(description = "Distributed trace correlation ID", example = "corr-int-12345")
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
