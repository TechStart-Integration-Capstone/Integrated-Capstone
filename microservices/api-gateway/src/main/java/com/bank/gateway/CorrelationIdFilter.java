package com.bank.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Injects an X-Correlation-ID header on every request passing through the gateway.
 *
 * Behaviour:
 *   - If the client already sent an X-Correlation-ID, it is preserved as-is.
 *   - If not, a new UUID is generated and injected.
 *   - The ID is forwarded downstream on the request AND echoed back on the response,
 *     so clients can correlate their request to logs in Grafana/Loki.
 *
 * Runs at order -99 (just after JwtAuthFilter at -100) so the ID is available
 * to all downstream filters and services.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Preserve existing ID from client, or generate a new one
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        // Forward the ID downstream on the request
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Echo the ID back on the response so the client can read it
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        mutatedExchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        log.debug("[gateway] correlationId={} method={} path={}",
                finalCorrelationId,
                request.getMethod(),
                request.getURI().getPath());

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Just after JwtAuthFilter (-100) so JWT claims are already resolved
        return -99;
    }
}
