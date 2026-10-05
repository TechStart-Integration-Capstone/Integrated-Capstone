package com.bank.transaction.client;

import com.bank.transaction.dto.T24Result;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Client for T24 Core Adapter (Phase 4).
 * Protected by Resilience4j circuit breaker "t24Adapter".
 */
@Component
public class T24AdapterClient {

    private static final Logger log = LoggerFactory.getLogger(T24AdapterClient.class);
    private final RestClient restClient;

    public T24AdapterClient(@Value("${app.t24-adapter.url:http://t24-adapter:8090/api/v1/t24/transfer}") String t24Url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(t24Url)
                .requestFactory(factory)
                .build();
    }

    @CircuitBreaker(name = "t24Adapter", fallbackMethod = "t24AdapterFallback")
    public T24Result executeTransfer(String referenceNo, String debitAccountNo, String creditAccountNo, BigDecimal amount, String currency, String correlationId) {
        log.info("[remittance-orchestrator] Calling T24 Adapter for ref={} amount={} correlationId={}",
                referenceNo, amount, correlationId);

        Map<String, Object> body = Map.of(
                "referenceNo", referenceNo,
                "debitAccountNo", debitAccountNo,
                "creditAccountNo", creditAccountNo,
                "amount", amount,
                "currency", currency != null ? currency : "PHP"
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "unknown")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return new T24Result("REJECTED", null, null, "Empty T24 response", false);
            }

            String status = (String) response.get("status");
            String ftReference = (String) response.get("ftReference");
            String ofsResponse = (String) response.get("ofsResponse");
            String reason = (String) response.get("reason");
            Boolean cached = (Boolean) response.get("cachedResponse");

            return new T24Result(status, ftReference, ofsResponse, reason, Boolean.TRUE.equals(cached));

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 422) {
                log.warn("[remittance-orchestrator] T24 returned 422 Unprocessable (rejection): {}", e.getResponseBodyAsString());
                return new T24Result("REJECTED", null, null, "T24 Core Rejection /-1", false);
            } else if (e.getStatusCode().value() == 202) {
                log.warn("[remittance-orchestrator] T24 returned 202 Accepted (processing timeout)");
                return new T24Result("PROCESSING", null, null, "T24 SLA processing delay", false);
            }
            log.error("[remittance-orchestrator] T24 error status {}: {}", e.getStatusCode(), e.getMessage());
            return new T24Result("PROCESSING", null, null, "T24 HTTP error " + e.getStatusCode(), false);
        }
    }

    public T24Result t24AdapterFallback(String referenceNo, String debitAccountNo, String creditAccountNo, BigDecimal amount, String currency, String correlationId, Throwable t) {
        log.error("[remittance-orchestrator] T24 Adapter fallback triggered! Error: {}", t.getMessage());
        return new T24Result("PROCESSING", null, null, "T24 Core Adapter circuit breaker OPEN / unavailable", false);
    }
}
