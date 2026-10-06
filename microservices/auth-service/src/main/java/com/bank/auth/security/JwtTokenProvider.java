package com.bank.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {
    private final Key key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.security.jwt-secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret,
            @Value("${app.security.jwt-expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long customerId, String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("customerId", customerId)
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long customerId(String authorization) {
        Claims claims = claims(authorization);
        Number id = claims.get("customerId", Number.class);
        if (id == null || id.longValue() < 0) throw new IllegalArgumentException("Invalid customer");
        return id.longValue();
    }

    public boolean isAdmin(String authorization) {
        Object roles = claims(authorization).get("roles");
        return roles instanceof List<?> list && list.contains("ROLE_ADMIN");
    }

    private Claims claims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(authorization.substring(7)).getBody();
    }
}
