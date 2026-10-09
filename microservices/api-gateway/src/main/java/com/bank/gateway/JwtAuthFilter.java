package com.bank.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final Key signingKey;

    // Paths that don't require a JWT
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/banking/login",
            "/api/v1/auth/banking/register",
            "/api/v1/risk/health",
            "/api/v1/risk/docs",
            "/api/v1/risk/openapi",
            "/api/v1/t24/health",
            "/api/v1/remittance/health",
            "/actuator"
    );

    // Paths that additionally require ROLE_ADMIN in the JWT
    private static final List<String> ADMIN_PREFIXES = List.of(
            "/api/v1/loans/eod",
            "/api/v1/interest/eod",
            "/api/v1/auth/admin",
            "/api/v1/transactions/admin",
            "/api/v1/stress",
            "/api/v1/ledger",
            "/api/v1/t24",
            "/api/v1/risk"
    );

    static boolean isAdminPath(String path) {
        if (ADMIN_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (path.startsWith("/api/v1/accounts/") &&
                (path.endsWith("/reset-balance") || path.endsWith("/status") || path.contains("/status/"))) {
            return true;
        }
        return false;
    }

    public JwtAuthFilter(
            @Value("${app.security.jwt-secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Step 1: Unconditionally strip incoming X-Auth-* headers to prevent spoofing
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(r -> r
                        .headers(h -> {
                            h.remove("X-Auth-Username");
                            h.remove("X-Auth-Customer-Id");
                            h.remove("X-Auth-Roles");
                            h.remove("X-Internal-Service");
                        })
                )
                .build();

        // Bypass authentication for HTTP OPTIONS preflight requests (CORS)
        if ("OPTIONS".equalsIgnoreCase(sanitizedExchange.getRequest().getMethod().name())) {
            sanitizedExchange.getResponse().setStatusCode(HttpStatus.OK);
            return sanitizedExchange.getResponse().setComplete();
        }

        String path = sanitizedExchange.getRequest().getURI().getPath();

        // Allow public paths through
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        if (isPublic) {
            String authHeader = sanitizedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 7) {
                try {
                    String token = authHeader.substring(7);
                    Claims claims = Jwts.parserBuilder()
                            .setSigningKey(signingKey)
                            .build()
                            .parseClaimsJws(token)
                            .getBody();
                    return chain.filter(withIdentity(sanitizedExchange, claims));
                } catch (JwtException | IllegalArgumentException ignored) {}
            }
            return chain.filter(sanitizedExchange);
        }

        String authHeader = sanitizedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() <= 7) {
            sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return sanitizedExchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (isAdminPath(path) && !roles(claims).contains("ROLE_ADMIN")) {
                sanitizedExchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return sanitizedExchange.getResponse().setComplete();
            }

            // Forward user info as headers to downstream services
            return chain.filter(withIdentity(sanitizedExchange, claims));
        } catch (JwtException | IllegalArgumentException e) {
            sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return sanitizedExchange.getResponse().setComplete();
        }
    }

    private static ServerWebExchange withIdentity(ServerWebExchange exchange, Claims claims) {
        return exchange.mutate()
                .request(r -> r
                        .header("X-Auth-Username", claims.getSubject())
                        .header("X-Auth-Customer-Id", String.valueOf(claims.get("customerId")))
                        .header("X-Auth-Roles", String.join(",", roles(claims)))
                )
                .build();
    }

    private static List<String> roles(Claims claims) {
        return claims.get("roles") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
