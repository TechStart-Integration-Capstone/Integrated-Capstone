package com.bank.transaction.controller;

import com.bank.transaction.dto.AdminTransactionRow;
import com.bank.transaction.service.AdminTransactionMonitorService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * PayPink 2.0 — Admin Transaction Monitor Controller (Phase 4).
 * Serves today's live ledger movements to the Operations Desk.
 */
@RestController
@RequestMapping("/api/v1/transactions/admin")
@CrossOrigin(origins = "*")
public class AdminTransactionMonitorController {

    private final AdminTransactionMonitorService monitorService;

    public AdminTransactionMonitorController(AdminTransactionMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/today")
    public ResponseEntity<List<AdminTransactionRow>> getTodayTransactions(
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader) {

        // Validate ROLE_ADMIN (forwarded downstream from API Gateway)
        if (rolesHeader != null && !rolesHeader.contains("ROLE_ADMIN") && !rolesHeader.contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required.");
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitorService.today());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Request failed."));
    }
}
