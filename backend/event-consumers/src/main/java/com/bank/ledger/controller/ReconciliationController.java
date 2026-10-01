package com.bank.ledger.controller;

import com.bank.ledger.model.postgres.ReconciliationLog;
import com.bank.ledger.service.ReconciliationService;
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
        reconciliationService.runScheduledSystemWideReconciliation();
        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "message", "System-wide Oracle vs PostgreSQL 15-minute reconciliation sweep executed successfully."
        ));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<ReconciliationLog>> getLogs() {
        return ResponseEntity.ok(reconciliationService.getRecentReconciliationLogs());
    }
}
