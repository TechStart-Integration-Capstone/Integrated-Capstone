package com.bank.ledger.auth.controller;

import com.bank.ledger.auth.service.AuthService;
import com.bank.ledger.dto.AuthRequest;
import com.bank.ledger.dto.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null) {
            authService.logout(authHeader);
        }
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Session successfully terminated. Token invalidated in Redis in-memory matrix."
        ));
    }

    @GetMapping("/demo-token")
    public ResponseEntity<AuthResponse> getDemoToken() {
        return ResponseEntity.ok(authService.authenticate(new AuthRequest("jdelacruz", "password123")));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String jti) {
        boolean active = authService.isTokenActive(jti);
        return ResponseEntity.ok(Map.of("jti", jti, "active", active));
    }
}
