package com.bank.transaction.controller;

import com.bank.transaction.dto.AdminTransactionRow;
import com.bank.transaction.service.AdminTransactionMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Transaction Monitor (Admin)", description = "Operations Desk live transaction monitoring (Phase 4 CQRS)")
@RestController
@RequestMapping("/api/v1/transactions/admin")
@CrossOrigin(origins = "*")
public class AdminTransactionMonitorController {

    private final AdminTransactionMonitorService monitorService;

    public AdminTransactionMonitorController(AdminTransactionMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Operation(summary = "Get today's live transactions (Admin)", description = "Retrieves today's live ledger transactions for real-time operations desk monitoring. Requires ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Live transactions returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_ADMIN")
    })
    @GetMapping("/today")
    public ResponseEntity<List<AdminTransactionRow>> getTodayTransactions(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader) {

        // Validate ROLE_ADMIN (forwarded downstream from API Gateway) — fail closed
        if (rolesHeader == null || java.util.Arrays.stream(rolesHeader.split(",")).map(String::trim).noneMatch("ROLE_ADMIN"::equals)) {
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
