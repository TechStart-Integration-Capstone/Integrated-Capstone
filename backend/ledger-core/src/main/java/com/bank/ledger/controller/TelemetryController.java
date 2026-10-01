package com.bank.ledger.controller;

import com.bank.ledger.security.IdempotencyService;
import com.bank.ledger.service.TelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final IdempotencyService idempotencyService;

    public TelemetryController(TelemetryService telemetryService, IdempotencyService idempotencyService) {
        this.telemetryService = telemetryService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>(telemetryService.getTelemetrySnapshot());
        stats.put("redisAvgCheckLatencyMs", String.format("%.3f", idempotencyService.getAverageCheckLatencyMs()));
        stats.put("redisCacheEntries", idempotencyService.getCacheSize());
        stats.put("redisHits", idempotencyService.getHitCount());
        stats.put("redisMisses", idempotencyService.getMissCount());
        stats.put("hikariPoolMax", 30);
        stats.put("hikariPoolMinIdle", 5);
        return ResponseEntity.ok(stats);
    }
}
