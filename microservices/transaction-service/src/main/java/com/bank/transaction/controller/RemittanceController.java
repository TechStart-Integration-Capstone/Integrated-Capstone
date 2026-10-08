package com.bank.transaction.controller;

import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.service.RemittanceOrchestratorService;
import com.bank.transaction.service.RemittanceSagaWorker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Customer Remittance & Transfers", description = "Customer money transfer saga: idempotency, real-time fraud scoring, T24 Core holds, and ledger settlement")
@RestController
@RequestMapping("/api/v1/remittance")
public class RemittanceController {

    private static final Logger log = LoggerFactory.getLogger(RemittanceController.class);

    private final RemittanceOrchestratorService orchestratorService;
    private final RemittanceSagaWorker sagaWorker;

    @Autowired
    public RemittanceController(RemittanceOrchestratorService orchestratorService, RemittanceSagaWorker sagaWorker) {
        this.orchestratorService = orchestratorService;
        this.sagaWorker = sagaWorker;
    }

    /** Without "Send now" support (unit tests that only exercise the orchestrator). */
    public RemittanceController(RemittanceOrchestratorService orchestratorService) {
        this(orchestratorService, null);
    }

    @Operation(summary = "Remittance service health check", description = "Verifies Remittance Orchestrator service operational status.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "remittance-orchestrator",
                "version", "2.0.0"
        ));
    }

    @Operation(summary = "Initiate remittance transfer", description = "Customer money transfer executing a 4-step distributed saga: Idempotency locking -> Two-layer fraud scoring -> T24 Core hold placement -> Double-entry ledger settlement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer completed and posted"),
            @ApiResponse(responseCode = "202", description = "Transfer accepted into cancellation grace window"),
            @ApiResponse(responseCode = "400", description = "Missing required header or invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid customer identity"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds, limit exceeded, or rejected by fraud engine")
    })
    @PostMapping("/transfer")
    public ResponseEntity<RemittanceResponse> processRemittance(
            @Valid @RequestBody RemittanceRequest request,
            @Parameter(description = "Unique idempotency key to prevent double-submits", example = "remit-idemp-12345", required = true)
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Parameter(description = "Distributed trace correlation ID", example = "corr-remit-98765")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String authCustomerIdHeader) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Missing required header: Idempotency-Key");
        }

        if (authCustomerIdHeader == null || authCustomerIdHeader.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing identity context: X-Auth-Customer-Id header required");
        }

        Long callerCustomerId;
        try {
            callerCustomerId = Long.valueOf(authCustomerIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid X-Auth-Customer-Id header format");
        }

        log.info("[remittance-controller] Transfer request received from callerCustomerId={} sourceAcc={} to targetAcc={} amount={}",
                callerCustomerId, request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount());

        RemittanceResponse response = orchestratorService.processRemittance(request, idempotencyKey, correlationId, callerCustomerId);

        if ("PROCESSING".equalsIgnoreCase(response.getStatus()) || Remittance.STATUS_RESERVED.equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(202).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel pending transfer", description = "Cancels a transfer during its cancellation grace window, releasing reserved funds back to available balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer successfully cancelled"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid customer identity"),
            @ApiResponse(responseCode = "409", description = "Transfer is already settled or cannot be cancelled")
    })
    @PostMapping("/{referenceNo}/cancel")
    public ResponseEntity<Map<String, Object>> cancelRemittance(
            @Parameter(description = "Remittance reference number", example = "REM-202610-001")
            @PathVariable String referenceNo,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String authCustomerIdHeader) {

        if (authCustomerIdHeader == null || authCustomerIdHeader.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing identity context: X-Auth-Customer-Id header required");
        }

        Long callerCustomerId;
        try {
            callerCustomerId = Long.valueOf(authCustomerIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid X-Auth-Customer-Id header format");
        }

        log.info("[remittance-controller] Cancel request received for ref={} from customerId={}", referenceNo, callerCustomerId);
        orchestratorService.cancelTransferByUser(referenceNo, callerCustomerId);

        return ResponseEntity.ok(Map.of(
                "referenceNo", referenceNo,
                "status", Remittance.STATUS_CANCELLED,
                "message", "Transfer was successfully cancelled. Held funds have been released back to your available balance."
        ));
    }

    /** Skips the rest of the cancellation window and submits the transfer to core banking now. */
    @Operation(summary = "Send transfer immediately", description = "Skips the remaining cancellation grace window and immediately settles the transfer with T24 Core.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer immediately submitted for settlement"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid customer identity")
    })
    @PostMapping("/{referenceNo}/send-now")
    public ResponseEntity<Map<String, Object>> sendNow(
            @Parameter(description = "Remittance reference number", example = "REM-202610-001")
            @PathVariable String referenceNo,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String authCustomerIdHeader) {

        if (authCustomerIdHeader == null || authCustomerIdHeader.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing identity context: X-Auth-Customer-Id header required");
        }
        Long callerCustomerId;
        try {
            callerCustomerId = Long.valueOf(authCustomerIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid X-Auth-Customer-Id header format");
        }

        log.info("[remittance-controller] Send-now request received for ref={} from customerId={}", referenceNo, callerCustomerId);
        Remittance remittance = sagaWorker.sendNow(referenceNo, callerCustomerId);
        return ResponseEntity.ok(Map.of("referenceNo", referenceNo, "status", remittance.getStatus()));
    }

    @Operation(summary = "Get remittance transfer status", description = "Queries the current lifecycle state (RESERVED, COMPLETED, CANCELLED, or FAILED) of a transfer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer status retrieved"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid customer identity"),
            @ApiResponse(responseCode = "404", description = "Transfer reference not found")
    })
    @GetMapping("/{referenceNo}/status")
    public ResponseEntity<RemittanceResponse> getRemittanceStatus(
            @Parameter(description = "Remittance reference number", example = "REM-202610-001")
            @PathVariable String referenceNo,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String authCustomerIdHeader) {

        if (authCustomerIdHeader == null || authCustomerIdHeader.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing identity context: X-Auth-Customer-Id header required");
        }

        Long callerCustomerId;
        try {
            callerCustomerId = Long.valueOf(authCustomerIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid X-Auth-Customer-Id header format");
        }

        RemittanceResponse statusResponse = orchestratorService.getRemittanceStatus(referenceNo, callerCustomerId);
        return ResponseEntity.ok(statusResponse);
    }
}
