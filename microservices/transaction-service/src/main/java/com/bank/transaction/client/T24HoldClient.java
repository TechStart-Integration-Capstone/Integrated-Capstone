package com.bank.transaction.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Client for T24 Core Banking Hold Engine (Phase 3).
 * Interacts with T24 Core to lock and release funds without direct SQL balance mutations.
 */
@Component
public class T24HoldClient {

    private static final Logger log = LoggerFactory.getLogger(T24HoldClient.class);
    private final RestClient restClient;

    public T24HoldClient(@Value("${app.t24-adapter.base-url:http://t24-adapter:8090/api/v1/t24}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public record HoldResult(boolean success, Long holdId, String referenceNo, String status, String errorMessage) {}

    @CircuitBreaker(name = "t24Hold", fallbackMethod = "placeHoldFallback")
    public HoldResult placeHold(Long accountId, String accountNumber, BigDecimal amount, String currency, String referenceNo) {
        log.info("[t24-hold-client] Requesting T24 Core hold for ref={} accountId={} amount={}",
                referenceNo, accountId, amount);

        Map<String, Object> body = Map.of(
                "accountId", accountId != null ? accountId : 0L,
                "amount", amount,
                "currency", currency != null ? currency : "PHP",
                "referenceNo", referenceNo
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/holds/lock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && "ACTIVE".equalsIgnoreCase((String) response.get("status"))) {
                Long holdId = response.get("holdId") != null ? ((Number) response.get("holdId")).longValue() : null;
                return new HoldResult(true, holdId, referenceNo, "ACTIVE", null);
            }
            return new HoldResult(false, null, referenceNo, "REJECTED", "Hold not confirmed by T24 Core");

        } catch (HttpStatusCodeException e) {
            log.warn("[t24-hold-client] T24 Core returned HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new HoldResult(false, null, referenceNo, "FAILED", e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[t24-hold-client] Error calling T24 Core hold API: {}", e.getMessage());
            return new HoldResult(false, null, referenceNo, "ERROR", e.getMessage());
        }
    }

    public HoldResult placeHoldFallback(Long accountId, String accountNumber, BigDecimal amount, String currency, String referenceNo, Throwable t) {
        log.error("[t24-hold-client] Fallback triggered placing hold for ref={}: {}", referenceNo, t.getMessage());
        return new HoldResult(false, null, referenceNo, "CIRCUIT_OPEN", "T24 Hold circuit open or unavailable: " + t.getMessage());
    }

    public boolean releaseHold(String referenceNo) {
        log.info("[t24-hold-client] Requesting T24 Core hold release for ref={}", referenceNo);
        Map<String, Object> body = Map.of("referenceNo", referenceNo);

        try {
            restClient.post()
                    .uri("/holds/release")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("[t24-hold-client] Failed to release hold in T24 Core for ref={}: {}", referenceNo, e.getMessage());
            return false;
        }
    }
}
