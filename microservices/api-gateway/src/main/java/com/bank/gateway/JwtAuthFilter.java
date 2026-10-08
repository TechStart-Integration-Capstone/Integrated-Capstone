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
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars"
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
            "/api/v1/risk",
            "/api/v1/reconciliation",
            "/api/v1/audit",
            "/api/v1/analytics",
            "/api/v1/telemetry"
    );

    static boolean isAdminPath(String path) {
        if (ADMIN_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (path.startsWith("/api/v1/accounts/") &&
                (path.endsWith("/reset-balance") || path.endsWith("/status") || path.contains("/status/"))) {
            return true;
        }
        if (path.startsWith("/api/v1/loans/applications/") &&
                (path.endsWith("/retry") || path.endsWith("/reset"))) {
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
            return writeProblem(sanitizedExchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Full authentication is required to access this resource.");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (isAdminPath(path) && !roles(claims).contains("ROLE_ADMIN")) {
                return writeProblem(sanitizedExchange, HttpStatus.FORBIDDEN, "Forbidden",
                        "Administrative privileges (ROLE_ADMIN) are required to access this resource.");
            }

            // Forward user info as headers to downstream services
            return chain.filter(withIdentity(sanitizedExchange, claims));
        } catch (JwtException | IllegalArgumentException e) {
            return writeProblem(sanitizedExchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "The provided authentication token is invalid or expired.");
        }
    }

    private static final org.springframework.http.MediaType PROBLEM_JSON =
            org.springframework.http.MediaType.parseMediaType("application/problem+json");

    private static Mono<Void> writeProblem(ServerWebExchange exchange, HttpStatus status, String title, String detail) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(PROBLEM_JSON);
        String path = exchange.getRequest().getURI().getPath();
        String json = String.format(
                "{\"type\":\"https://paypink.ph/errors/%d\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"instance\":\"%s\"}",
                status.value(),
                title,
                status.value(),
                detail,
                path
        );
        org.springframework.core.io.buffer.DataBuffer buffer =
                response.bufferFactory().wrap(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
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
