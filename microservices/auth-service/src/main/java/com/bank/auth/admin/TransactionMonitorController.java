package com.bank.auth.admin;

import com.bank.auth.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
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

/**
 * @deprecated Deprecated in PayPink 2.0 (Phase 6). API Gateway routes /api/v1/auth/admin/transactions/today
 * to transaction-service (/api/v1/transactions/admin/today).
 */
@Deprecated
@Tag(name = "Transaction Monitor (Admin)", description = "Operations Desk live transaction monitoring (Deprecated: routed to transaction-service)")
@RestController
@RequestMapping("/api/v1/auth/admin/transactions")
public class TransactionMonitorController {
    private final JwtTokenProvider tokens;
    private final TransactionMonitorService monitor;

    public TransactionMonitorController(JwtTokenProvider tokens, TransactionMonitorService monitor) {
        this.tokens = tokens;
        this.monitor = monitor;
    }

    @Operation(summary = "Get today's live transactions (Deprecated)", description = "Deprecated in Phase 6: routed to transaction-service /api/v1/transactions/admin/today.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of today's transactions"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - admin sign in required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_ADMIN")
    })
    @GetMapping("/today")
    public ResponseEntity<List<TransactionMonitorService.Row>> today(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization) {
        boolean admin;
        try {
            admin = tokens.isAdmin(authorization);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in as an administrator.");
        }
        if (!admin) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(monitor.today());
    }
}
