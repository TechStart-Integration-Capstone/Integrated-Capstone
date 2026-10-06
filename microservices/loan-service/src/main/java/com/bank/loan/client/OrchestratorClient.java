package com.bank.loan.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls transaction-service's internal transfer endpoint — the only way loan money moves.
 *
 * Outcomes:
 *   POSTED       — money moved; transactionId and ftReference are set
 *   REJECTED     — nothing moved (reason INSUFFICIENT_FUNDS for a short balance)
 *   PENDING_CORE — outcome unknown (T24 processing, timeout, orchestrator down). Retrying with the
 *                  same idempotency key is safe: the orchestrator replays instead of moving money twice.
 */
@Component
public class OrchestratorClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);
    public static final String INTERNAL_PATH = "/internal/remittance/transfer";
    public static final String REASON_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    private final WebClient webClient;
    private final Duration timeout;

    public record TransferResult(String status, Long transactionId, String ftReference, String reason) {
        public boolean isPosted() { return "POSTED".equals(status); }
        public boolean isInsufficientFunds() { return "REJECTED".equals(status) && REASON_INSUFFICIENT_FUNDS.equals(reason); }
        static TransferResult pendingCore(String reason) { return new TransferResult("PENDING_CORE", null, null, reason); }
    }

    public OrchestratorClient(@Value("${app.orchestrator.url:http://transaction-service:8083}") String baseUrl,
                              @Value("${app.orchestrator.timeout-ms:10000}") long timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.create().responseTimeout(timeout);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public TransferResult transfer(String sourceAccountNo, String targetAccountNo, BigDecimal amount,
                                   String transactionType, String idempotencyKey, String reason, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceAccountNo", sourceAccountNo);
        body.put("targetAccountNo", targetAccountNo);
        body.put("amount", amount);
        body.put("transactionType", transactionType);
        body.put("idempotencyKey", idempotencyKey);
        body.put("reason", reason);

        try {
            TransferResult result = webClient.post()
                    .uri(INTERNAL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Service", "loan-service")
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "loan-" + idempotencyKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(TransferResult.class)
                    .block(timeout);
            if (result == null || result.status() == null) {
                return TransferResult.pendingCore("Empty response from orchestrator");
            }
            log.info("[loan-service] {} key={} -> {} txId={} ft={} reason={}", transactionType, idempotencyKey,
                    result.status(), result.transactionId(), result.ftReference(), result.reason());
            return result;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.error("[loan-service] Orchestrator refused {} key={}: {} {}", transactionType, idempotencyKey,
                        e.getStatusCode().value(), e.getResponseBodyAsString());
                return new TransferResult("REJECTED", null, null, "Orchestrator refused request: " + e.getStatusCode().value());
            }
            log.warn("[loan-service] Orchestrator error {} for key={}", e.getStatusCode().value(), idempotencyKey);
            return TransferResult.pendingCore("Orchestrator error " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            // Timeout or connection failure: the transfer may still complete on the other side.
            log.warn("[loan-service] Orchestrator unreachable/timeout for key={}: {}", idempotencyKey, e.getMessage());
            return TransferResult.pendingCore("Orchestrator timeout or unavailable");
        }
    }
}
