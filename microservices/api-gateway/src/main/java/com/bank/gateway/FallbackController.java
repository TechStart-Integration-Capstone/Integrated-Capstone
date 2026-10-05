package com.bank.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback endpoints invoked by the gateway CircuitBreaker filter
 * when a downstream service is unavailable or the circuit is OPEN.
 *
 * Returns RFC-7807 Problem Details so the client gets a structured error
 * instead of a raw 500/404 from the gateway.
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /**
     * Fallback for transaction-service (/api/v1/ledger/**).
     * Circuit opens after repeated mutation failures (transactionCb config).
     * Returns 503 — client should retry with the same Idempotency-Key.
     */
    @RequestMapping("/fallback/transaction")
    public ResponseEntity<Map<String, Object>> transactionFallback(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        log.warn("[gateway] transactionCb circuit OPEN — returning 503. correlationId={}",
                correlationId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "type",       "https://paypink.ph/errors/service-unavailable",
                "title",      "Transaction Service Unavailable",
                "status",     503,
                "detail",     "The transaction service is temporarily unavailable. " +
                              "Your request was not processed. " +
                              "Retry using the same Idempotency-Key.",
                "correlationId", correlationId != null ? correlationId : "unknown",
                "timestamp",  Instant.now().toString()
        ));
    }
}
