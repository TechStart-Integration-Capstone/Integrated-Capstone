package com.bank.auth.admin;

import com.bank.auth.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/admin/transactions")
public class TransactionMonitorController {
    private final JwtTokenProvider tokens;
    private final TransactionMonitorService monitor;

    public TransactionMonitorController(JwtTokenProvider tokens, TransactionMonitorService monitor) {
        this.tokens = tokens;
        this.monitor = monitor;
    }

    @GetMapping("/today")
    public ResponseEntity<List<TransactionMonitorService.Row>> today(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
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
