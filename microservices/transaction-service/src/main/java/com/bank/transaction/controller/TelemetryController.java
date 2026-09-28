package com.bank.transaction.controller;

import com.bank.transaction.service.TelemetryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes live performance stats to the frontend Observability tab (Tab 6).
 * Route: GET /api/v1/telemetry/stats
 * Proxied through the API Gateway → transaction-service.
 */
@RestController
@RequestMapping("/api/v1/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final StringRedisTemplate redisTemplate;

    public TelemetryController(TelemetryService telemetryService,
                               StringRedisTemplate redisTemplate) {
        this.telemetryService = telemetryService;
        this.redisTemplate    = redisTemplate;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>(telemetryService.getTelemetrySnapshot());

        // Redis round-trip latency probe (live measurement)
        long rStart = System.nanoTime();
        try {
            redisTemplate.opsForValue().get("__telemetry_probe__");
        } catch (Exception ignored) {}
        double redisLatencyMs = (System.nanoTime() - rStart) / 1_000_000.0;

        stats.put("redisAvgCheckLatencyMs", String.format("%.3f", redisLatencyMs));
        stats.put("hikariPoolMax",   30);
        stats.put("hikariPoolMinIdle", 5);

        return ResponseEntity.ok(stats);
    }
}
