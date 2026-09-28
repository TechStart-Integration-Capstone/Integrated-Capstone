package com.bank.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration for the API Gateway.
 *
 * Uses Spring Cloud Gateway's built-in Redis token-bucket rate limiter.
 *
 * Algorithm: Token Bucket
 *   - replenishRate  : tokens added to the bucket per second (steady-state limit)
 *   - burstCapacity  : maximum tokens the bucket can hold (allows short bursts)
 *   - requestedTokens: tokens consumed per request (default 1)
 *
 * Rates applied per-user (keyed on X-Auth-Username header set by JwtAuthFilter).
 * Falls back to the remote IP address if the header is absent (e.g. unauthenticated
 * requests that slip past the public-path whitelist — they get the same bucket).
 *
 * Applied to:
 *   - /api/v1/ledger/**   (balance mutations — the hot write path)
 *   - /api/v1/stress/**   (stress test endpoint — concurrent thread bursts)
 *
 * Read paths (accounts, reconciliation, analytics, telemetry) are intentionally
 * NOT rate-limited here — they are idempotent reads with negligible write load.
 */
@Configuration
public class GatewayConfig {

    /**
     * KeyResolver — per-authenticated-user rate limiting.
     *
     * JwtAuthFilter (which runs before the rate limiter) sets the
     * X-Auth-Username header from the validated JWT subject claim.
     * Using the username as the key means each customer has their own
     * independent token bucket — one user cannot exhaust another's quota.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String username = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Auth-Username");

            // Fall back to remote address for unauthenticated requests
            if (username == null || username.isBlank()) {
                String remoteAddr = exchange.getRequest()
                        .getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
                return Mono.just("anon:" + remoteAddr);
            }

            return Mono.just(username);
        };
    }

    /**
     * RedisRateLimiter bean — token bucket parameters.
     *
     *   replenishRate  = 100  → 100 requests/second per user (steady state)
     *   burstCapacity  = 200  → allows up to 200 req in a single burst
     *   requestedTokens = 1  → each request costs 1 token
     *
     * At peak load (JMeter stress test at 800 TPS across multiple users)
     * each individual user will rarely exceed 100 req/s, so legitimate
     * traffic is unaffected. A single misbehaving client is throttled at 200.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }
}
