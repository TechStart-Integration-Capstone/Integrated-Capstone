package com.bank.outbox.controller;

import com.bank.outbox.service.OutboxPollerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight status endpoint so the outbox-publisher can be observed
 * from Grafana / the frontend telemetry tab without full actuator exposure.
 *
 * GET /api/v1/outbox/status
 *   → { totalPublished, totalFailed, totalDeadLetter }
 */
@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxStatusController {

    private final OutboxPollerService pollerService;

    public OutboxStatusController(OutboxPollerService pollerService) {
        this.pollerService = pollerService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "totalPublished",  pollerService.getTotalPublished(),
                "totalFailed",     pollerService.getTotalFailed(),
                "totalDeadLetter", pollerService.getTotalDeadLetter(),
                "service",         "outbox-publisher",
                "status",          "UP"
        ));
    }
}
