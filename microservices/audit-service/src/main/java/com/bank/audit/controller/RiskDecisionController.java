package com.bank.audit.controller;

import com.bank.audit.model.RiskDecision;
import com.bank.audit.repository.RiskDecisionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayPink 2.0 — Phase 6 Risk Decision Query API.
 *
 * Exposes read-only access to the RISK_DECISION immutable audit log in PostgreSQL.
 * Routed via api-gateway at /api/v1/audit/risk-decisions (requires JWT).
 *
 * Endpoints
 * ---------
 *  GET /risk-decisions               — paginated list, newest first
 *  GET /risk-decisions?decision=     — filter by APPROVE | REJECT | UNAVAILABLE
 *  GET /risk-decisions?from=&to=     — filter by scored_at range (ISO-8601)
 *  GET /risk-decisions/{referenceNo} — single decision by reference number
 *  GET /risk-decisions/stats         — approval/rejection counts for last 24 h / 7 d
 */
@RestController
@RequestMapping({"/risk-decisions", "/audit/risk-decisions"})
public class RiskDecisionController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE     = 200;

    private final RiskDecisionRepository repository;

    public RiskDecisionController(RiskDecisionRepository repository) {
        this.repository = repository;
    }

    /**
     * Paginated list of risk decisions.
     * Optional query params: decision, from, to, page (0-based), size.
     */
    @GetMapping
    public ResponseEntity<Page<RiskDecision>> list(
            @RequestParam(required = false)                                    String decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, clampedSize);

        if (decision != null && !decision.isBlank()) {
            return ResponseEntity.ok(
                    repository.findByDecisionOrderByScoredAtDesc(decision.toUpperCase(), pageable));
        }
        if (from != null && to != null) {
            return ResponseEntity.ok(
                    repository.findByScoredAtBetweenOrderByScoredAtDesc(from, to, pageable));
        }
        return ResponseEntity.ok(repository.findAllByOrderByScoredAtDesc(pageable));
    }

    /**
     * Single decision by remittance reference number.
     * Returns 404 if no decision has been recorded for that reference yet.
     */
    @GetMapping("/{referenceNo}")
    public ResponseEntity<RiskDecision> getByReference(@PathVariable String referenceNo) {
        return repository.findByReferenceNo(referenceNo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Approval / rejection summary statistics.
     * Returns counts grouped by decision for the last 24 hours and last 7 days.
     * Useful for Grafana panels and the admin portal.
     *
     * Response shape:
     * {
     *   "last24h": { "APPROVE": 142, "REJECT": 8, "UNAVAILABLE": 0 },
     *   "last7d":  { "APPROVE": 891, "REJECT": 43, "UNAVAILABLE": 2 }
     * }
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Map<String, Long>>> stats() {
        OffsetDateTime now    = OffsetDateTime.now();
        OffsetDateTime ago24h = now.minusHours(24);
        OffsetDateTime ago7d  = now.minusDays(7);

        Map<String, Map<String, Long>> result = new HashMap<>();
        result.put("last24h", toCounts(repository.countByDecisionSince(ago24h)));
        result.put("last7d",  toCounts(repository.countByDecisionSince(ago7d)));

        return ResponseEntity.ok(result);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Long> toCounts(List<Object[]> rows) {
        // Pre-fill all expected decision values with 0 so callers always get all three keys
        Map<String, Long> map = new HashMap<>();
        map.put("APPROVE",     0L);
        map.put("REJECT",      0L);
        map.put("UNAVAILABLE", 0L);
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }
}
