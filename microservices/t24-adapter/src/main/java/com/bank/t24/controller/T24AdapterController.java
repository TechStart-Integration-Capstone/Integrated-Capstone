package com.bank.t24.controller;

import com.bank.t24.dto.T24TransferRequest;
import com.bank.t24.dto.T24TransferResponse;
import com.bank.t24.service.T24ClientService;
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

import java.util.Map;

@Tag(name = "T24 Core Adapter", description = "Core banking integration adapter for Temenos T24 OFS operations")
@RestController
@RequestMapping("/api/v1/t24")
public class T24AdapterController {

    private static final Logger log = LoggerFactory.getLogger(T24AdapterController.class);

    private final T24ClientService t24ClientService;

    public T24AdapterController(T24ClientService t24ClientService) {
        this.t24ClientService = t24ClientService;
    }

    @Operation(summary = "T24 adapter health check", description = "Verifies T24 adapter operational readiness.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "t24-adapter",
                "version", "2.0.0"
        ));
    }

    @Operation(summary = "Execute core funds transfer", description = "Submits a transfer request to T24 Core Banking via OFS messaging.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer completed and posted"),
            @ApiResponse(responseCode = "202", description = "Transfer accepted for asynchronous processing"),
            @ApiResponse(responseCode = "422", description = "Transfer rejected by T24 Core validation rules")
    })
    @PostMapping("/transfer")
    public ResponseEntity<T24TransferResponse> processTransfer(
            @Valid @RequestBody T24TransferRequest request,
            @Parameter(description = "Distributed trace correlation ID", example = "corr-t24-12345")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("[t24-adapter] Received transfer request ref={} amount={} correlationId={}",
                request.getReferenceNo(), request.getAmount(), correlationId);

        T24TransferResponse response = t24ClientService.executeTransfer(request);

        if ("REJECTED".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        } else if ("PROCESSING".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
