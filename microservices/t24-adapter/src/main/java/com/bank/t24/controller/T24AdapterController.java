package com.bank.t24.controller;

import com.bank.t24.dto.T24TransferRequest;
import com.bank.t24.dto.T24TransferResponse;
import com.bank.t24.service.T24ClientService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/t24")
public class T24AdapterController {

    private static final Logger log = LoggerFactory.getLogger(T24AdapterController.class);

    private final T24ClientService t24ClientService;

    public T24AdapterController(T24ClientService t24ClientService) {
        this.t24ClientService = t24ClientService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "t24-adapter",
                "version", "2.0.0"
        ));
    }

    @PostMapping("/transfer")
    public ResponseEntity<T24TransferResponse> processTransfer(
            @Valid @RequestBody T24TransferRequest request,
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
