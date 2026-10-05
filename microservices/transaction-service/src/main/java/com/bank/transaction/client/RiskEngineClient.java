package com.bank.transaction.client;

import com.bank.transaction.dto.RiskResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for Python FastAPI Risk Engine (Phase 3).
 * Protected by Resilience4j circuit breaker "riskEngine".
 *
 * Layer 1 improvement: enriches every scoring request with three behavioural
 * signals that were designed into scorer.py but never sent before:
 *
 *   accountAgeHours   - hours since the source account was created
 *   recentTxCount     - transfers sent from this account in the last hour
 *   amountVsAvgRatio  - this amount divided by the 30-day average amount
 *
 * All queries are best-effort: a failure just omits that field from the request.
 */
@Component
public class RiskEngineClient {

    private static final Logger log = LoggerFactory.getLogger(RiskEngineClient.class);

    private final RestClient   restClient;
    private final JdbcTemplate jdbc;

    public RiskEngineClient(
            @Value("${app.risk-engine.url:http://risk-engine:8000/score}") String riskEngineUrl,
            JdbcTemplate jdbc) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(1500);

        this.restClient = RestClient.builder()
                .baseUrl(riskEngineUrl)
                .requestFactory(factory)
                .build();

        this.jdbc = jdbc;
    }

    @CircuitBreaker(name = "riskEngine", fallbackMethod = "riskEngineFallback")
    public RiskResult evaluateRisk(
            Object sourceAccountId,
            Object targetAccountId,
            BigDecimal amount,
            String currency,
            String correlationId) {

        log.info("[risk-client] Enriching risk request accountId={} amount={} corrId={}",
                sourceAccountId, amount, correlationId);

        Long srcId = toLong(sourceAccountId);

        Double  accountAgeHours  = queryAccountAgeHours(srcId);
        Integer recentTxCount    = queryRecentTxCount(srcId);
        Double  amountVsAvgRatio = queryAmountVsAvgRatio(srcId, amount);

        log.info("[risk-client] Enrichment accountAgeHours={} recentTxCount={} amountVsAvgRatio={}",
                accountAgeHours, recentTxCount, amountVsAvgRatio);

        Map<String, Object> body = new HashMap<>();
        body.put("accountId",       sourceAccountId);
        body.put("customerId",      sourceAccountId);
        body.put("amount",          amount);
        body.put("currency",        currency != null ? currency : "PHP");
        body.put("transactionType", "P2P_REMITTANCE");
        body.put("targetAccountId", targetAccountId);

        if (accountAgeHours  != null) body.put("accountAgeHours",  accountAgeHours);
        if (recentTxCount    != null) body.put("recentTxCount",     recentTxCount);
        if (amountVsAvgRatio != null) body.put("amountVsAvgRatio",  amountVsAvgRatio);

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

        double       scoreVal   = ((Number) response.get("score")).doubleValue();
        String       decision   = (String) response.get("decision");
        @SuppressWarnings("unchecked")
        List<String> reasons    = (List<String>) response.get("reasons");

        // ruleScore / mlScore / latencyMs are populated by risk engine v3.0.0+
        BigDecimal ruleScore  = response.get("ruleScore")  != null
                ? BigDecimal.valueOf(((Number) response.get("ruleScore")).doubleValue())  : null;
        BigDecimal mlScore    = response.get("mlScore")    != null
                ? BigDecimal.valueOf(((Number) response.get("mlScore")).doubleValue())    : null;
        Double     latencyMs  = response.get("latencyMs")  != null
                ? ((Number) response.get("latencyMs")).doubleValue()                      : null;

        log.info("[risk-client] Result score={} ruleScore={} mlScore={} decision={} reasons={} corrId={}",
                scoreVal, ruleScore, mlScore, decision, reasons, correlationId);

        return new RiskResult(BigDecimal.valueOf(scoreVal), decision, reasons, ruleScore, mlScore, latencyMs);
    }

    public RiskResult riskEngineFallback(
            Object sourceAccountId,
            Object targetAccountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            Throwable t) {

        log.error("[risk-client] Risk Engine fallback triggered! Error: {}", t.getMessage());
        return new RiskResult(BigDecimal.ONE, "UNAVAILABLE",
                List.of("risk_engine_unavailable: " + t.getMessage()));
    }

    // -----------------------------------------------------------------------
    // Private helpers — each is best-effort, returns null on any failure
    // -----------------------------------------------------------------------

    /** Hours since account was created. Enables new_account_under_24h rule. */
    private Double queryAccountAgeHours(Long accountId) {
        if (accountId == null) return null;
        try {
            String sql = "SELECT created_date FROM dbo.ACCOUNT WHERE account_id = ?";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, accountId);
            if (rows.isEmpty()) return null;
            Object raw = rows.get(0).get("created_date");
            if (!(raw instanceof java.sql.Timestamp ts)) return null;
            double hours = Duration.between(ts.toLocalDateTime(), LocalDateTime.now()).toMinutes() / 60.0;
            return Math.max(0.0, hours);
        } catch (Exception ex) {
            log.warn("[risk-client] Cannot query account age for accountId={}: {}", accountId, ex.getMessage());
            return null;
        }
    }

    /** Non-failed transfers from this account in the last hour. Enables velocity rules. */
    private Integer queryRecentTxCount(Long accountId) {
        if (accountId == null) return null;
        try {
            String sql =
                "SELECT COUNT(*) FROM dbo.REMITTANCE " +
                "WHERE source_account_id = ? " +
                "  AND created_at >= DATEADD(HOUR, -1, GETUTCDATE()) " +
                "  AND status NOT IN ('FAILED', 'CANCELLED')";
            Integer count = jdbc.queryForObject(sql, Integer.class, accountId);
            return count != null ? count : 0;
        } catch (Exception ex) {
            log.warn("[risk-client] Cannot query recent tx count for accountId={}: {}", accountId, ex.getMessage());
            return null;
        }
    }

    /**
     * Ratio of this transfer's amount to the account's 30-day average.
     * e.g. 8.0 means this transfer is 8x the customer's normal amount.
     * Returns null when there is no prior transfer history (first transfer ever).
     * Consumed by ml_scorer.py; rule-based scorer ignores unknown fields.
     */
    private Double queryAmountVsAvgRatio(Long accountId, BigDecimal amount) {
        if (accountId == null || amount == null) return null;
        try {
            String sql =
                "SELECT AVG(CAST(amount AS FLOAT)) FROM dbo.REMITTANCE " +
                "WHERE source_account_id = ? " +
                "  AND created_at >= DATEADD(DAY, -30, GETUTCDATE()) " +
                "  AND status IN ('POSTED', 'Reserved', 'Authorized', 'Processing')";
            Double avg = jdbc.queryForObject(sql, Double.class, accountId);
            if (avg == null || avg <= 0) return null;
            return amount.doubleValue() / avg;
        } catch (Exception ex) {
            log.warn("[risk-client] Cannot query avg ratio for accountId={}: {}", accountId, ex.getMessage());
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
