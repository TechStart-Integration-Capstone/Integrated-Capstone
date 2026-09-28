package com.bank.reconciliation.controller;

import com.bank.reconciliation.model.postgres.ReconciliationLog;
import com.bank.reconciliation.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reconciliation")
@CrossOrigin(origins = "*")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> triggerReconciliation() {
        reconciliationService.runFullSweep();
        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "message", "System-wide Oracle vs PostgreSQL 15-minute reconciliation sweep executed successfully."
        ));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<ReconciliationLog>> getLogs() {
        return ResponseEntity.ok(reconciliationService.getRecentLogs());
    }
}
