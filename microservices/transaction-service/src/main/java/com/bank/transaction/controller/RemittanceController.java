package com.bank.transaction.controller;

import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.service.RemittanceOrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/remittance")
public class RemittanceController {

    private static final Logger log = LoggerFactory.getLogger(RemittanceController.class);

    private final RemittanceOrchestratorService orchestratorService;

    public RemittanceController(RemittanceOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "remittance-orchestrator",
                "version", "2.0.0"
        ));
    }

    @PostMapping("/transfer")
    public ResponseEntity<RemittanceResponse> processRemittance(
            @Valid @RequestBody RemittanceRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("[remittance-controller] Transfer request received from sourceAcc={} to targetAcc={} amount={}",
                request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount());

        RemittanceResponse response = orchestratorService.processRemittance(request, idempotencyKey, correlationId);

        if ("PROCESSING".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(202).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
