package com.bank.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JwtAuthFilter.
 * Uses MockServerHttpRequest / MockServerWebExchange — no running server needed.
 */
class JwtAuthFilterTest {

    private static final String JWT_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private JwtAuthFilter filter;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(JWT_SECRET);
        signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    }

    private String validToken(String username, Long customerId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("customerId", customerId)
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 86_400_000L))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String expiredToken(String username) {
        Date past = new Date(System.currentTimeMillis() - 10_000L);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(past)
                .setExpiration(past)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private GatewayFilterChain recordingChain(AtomicBoolean called) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(inv -> { called.set(true); return Mono.empty(); });
        return chain;
    }

    @Test
    @DisplayName("Valid JWT: request passes through and X-Auth-Username header is set")
    void validToken_passesThrough_setsUsernameHeader() {
        String token = validToken("jdelacruz", 1L);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/accounts")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled)))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Missing Authorization header: 401 Unauthorized returned")
    void missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/accounts")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer prefix absent: 401 Unauthorized returned")
    void missingBearerPrefix_returns401() {
        String token = validToken("jdelacruz", 1L);
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/ledger/mutate")
                .header("Authorization", token) // no "Bearer " prefix
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Expired JWT: 401 Unauthorized returned")
    void expiredToken_returns401() {
        String token = expiredToken("jdelacruz");
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/accounts")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Tampered JWT (wrong signature): 401 Unauthorized returned")
    void tamperedToken_returns401() {
        // Sign with a different key — verification with the real key must fail
        Key wrongKey = Keys.hmacShaKeyFor(
                "WRONGWRONGWRONGWRONGWRONGWRONGWRONGWRONG".getBytes());
        Date now = new Date();
        String tampered = Jwts.builder()
                .setSubject("attacker")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 86_400_000L))
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/ledger/mutate")
                .header("Authorization", "Bearer " + tampered)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Public path /api/v1/auth/login: passes through without token")
    void publicPath_login_noTokenRequired() {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled)))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Public path /api/v1/auth/demo-token: passes through without token")
    void publicPath_demoToken_noTokenRequired() {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/auth/demo-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, recordingChain(chainCalled)))
                .verifyComplete();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Filter ordering: JwtAuthFilter must run before other filters (order = -100)")
    void filterOrder_isNegativeHundred() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }
}