package com.bank.ledger.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class GatewayRateLimiterConfig {

    /**
     * Requirement B6: Redis Rate Limiting KeyResolver.
     * Resolves rate limiting bucket by authenticated User or remote IP.
     */
    @Primary
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String authUser = exchange.getRequest().getHeaders().getFirst("X-Auth-Username");
            if (authUser != null && !authUser.isBlank()) {
                return Mono.just(authUser);
            }
            return Mono.just(
                    Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress()
            );
        };
    }
}
