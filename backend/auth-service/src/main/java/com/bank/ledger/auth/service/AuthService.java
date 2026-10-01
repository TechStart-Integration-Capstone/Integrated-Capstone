package com.bank.ledger.auth.service;

import com.bank.ledger.auth.model.Customer;
import com.bank.ledger.auth.repository.CustomerRepository;
import com.bank.ledger.auth.security.JwtTokenProvider;
import com.bank.ledger.dto.AuthRequest;
import com.bank.ledger.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    public static final String TOKEN_PREFIX = "token:";

    private final CustomerRepository customerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    // Fallback token matrix for local/test environments
    private final Map<String, Instant> fallbackTokenStore = new ConcurrentHashMap<>();

    public AuthService(CustomerRepository customerRepository,
                       JwtTokenProvider jwtTokenProvider,
                       @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.customerRepository = customerRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
    }

    public AuthResponse authenticate(AuthRequest request) {
        String username = request.getUsername();
        Customer customer = customerRepository.findByUsername(username)
                .orElseGet(() -> {
                    Customer c = new Customer(username, "hash", "Juan", "Dela Cruz", username + "@paypink.ph", "+63 917 888 1234");
                    return customerRepository.save(c);
                });

        List<String> roles;
        if (username.toLowerCase().contains("admin")) {
            roles = List.of("ROLE_ADMIN", "ROLE_CUSTOMER");
        } else if (username.toLowerCase().contains("teller")) {
            roles = List.of("ROLE_TELLER", "ROLE_CUSTOMER");
        } else {
            roles = List.of("ROLE_CUSTOMER", "ROLE_RETAIL_USER");
        }

        String jti = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateTokenWithJti(jti, customer.getCustomerId(), customer.getUsername(), roles);

        long expirationMs = jwtTokenProvider.getExpirationMs();
        registerActiveToken(jti, customer.getUsername(), expirationMs);

        return new AuthResponse(
                token,
                expirationMs,
                customer.getCustomerId(),
                customer.getUsername(),
                customer.getFirstName() + " " + customer.getLastName(),
                roles
        );
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        try {
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            String jti = jwtTokenProvider.getJtiFromToken(cleanToken);
            if (jti != null) {
                invalidateToken(jti);
            }
        } catch (Exception ignored) {}
    }

    public boolean isTokenActive(String jti) {
        if (jti == null) return false;
        try {
            if (redisTemplate != null) {
                Boolean exists = redisTemplate.hasKey(TOKEN_PREFIX + jti);
                return Boolean.TRUE.equals(exists);
            } else {
                Instant expiry = fallbackTokenStore.get(jti);
                return expiry != null && Instant.now().isBefore(expiry);
            }
        } catch (Exception e) {
            Instant expiry = fallbackTokenStore.get(jti);
            return expiry != null && Instant.now().isBefore(expiry);
        }
    }

    private void registerActiveToken(String jti, String username, long ttlMs) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(TOKEN_PREFIX + jti, username, ttlMs, TimeUnit.MILLISECONDS);
            }
            fallbackTokenStore.put(jti, Instant.now().plus(Duration.ofMillis(ttlMs)));
        } catch (Exception e) {
            fallbackTokenStore.put(jti, Instant.now().plus(Duration.ofMillis(ttlMs)));
        }
    }

    private void invalidateToken(String jti) {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(TOKEN_PREFIX + jti);
            }
            fallbackTokenStore.remove(jti);
        } catch (Exception e) {
            fallbackTokenStore.remove(jti);
        }
    }
}
