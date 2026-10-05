package com.bank.transaction.client;

import com.bank.transaction.dto.RiskResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Client for Python FastAPI Risk Engine (Phase 3).
 * Protected by Resilience4j circuit breaker "riskEngine".
 */
@Component
public class RiskEngineClient {

    private static final Logger log = LoggerFactory.getLogger(RiskEngineClient.class);
    private final RestClient restClient;

    public RiskEngineClient(@Value("${app.risk-engine.url:http://risk-engine:8000/score}") String riskEngineUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(1500);

        this.restClient = RestClient.builder()
                .baseUrl(riskEngineUrl)
                .requestFactory(factory)
                .build();
    }

    @CircuitBreaker(name = "riskEngine", fallbackMethod = "riskEngineFallback")
    public RiskResult evaluateRisk(Object sourceAccountId, Object targetAccountId, BigDecimal amount, String currency, String correlationId) {
        log.info("[remittance-orchestrator] Calling Risk Engine for accountId={} amount={} correlationId={}",
                sourceAccountId, amount, correlationId);

        Map<String, Object> body = Map.of(
                "accountId", sourceAccountId,
                "customerId", sourceAccountId, // caller customer mapping
                "amount", amount,
                "currency", currency != null ? currency : "PHP",
                "transactionType", "P2P_REMITTANCE",
                "targetAccountId", targetAccountId
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", correlationId != null ? correlationId : "unknown")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return new RiskResult(BigDecimal.ONE, "REJECT", List.of("empty_risk_engine_response"));
        }

        Double scoreVal = ((Number) response.get("score")).doubleValue();
        String decision = (String) response.get("decision");
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) response.get("reasons");

        return new RiskResult(BigDecimal.valueOf(scoreVal), decision, reasons);
    }

    public RiskResult riskEngineFallback(Object sourceAccountId, Object targetAccountId, BigDecimal amount, String currency, String correlationId, Throwable t) {
        log.error("[remittance-orchestrator] Risk Engine fallback triggered! Error: {}", t.getMessage());
        // AGENTS.md rule: No risk score -> reject (503 / REJECT). Money never moves without a score.
        return new RiskResult(BigDecimal.ONE, "UNAVAILABLE", List.of("risk_engine_unavailable: " + t.getMessage()));
    }
}
