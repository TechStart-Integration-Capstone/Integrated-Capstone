package com.bank.account.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Client for T24 Core Banking Account Inquiry (Phase 5).
 * Queries authoritative core account balances from t24-adapter.
 */
@Component
public class T24AccountClient {

    private static final Logger log = LoggerFactory.getLogger(T24AccountClient.class);
    private final RestClient restClient;
    private final String baseUrl;

    public T24AccountClient(@Value("${app.t24-adapter.base-url:http://t24-adapter:8090/api/v1/t24}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);

        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public record CoreBalance(
            Long accountId,
            String accountNumber,
            String accountType,
            String currency,
            BigDecimal currentBalance,
            BigDecimal heldBalance,
            BigDecimal availableBalance,
            String status
    ) {}

    public Optional<CoreBalance> getLiveBalance(String accountIdOrNumber) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.get()
                    .uri(this.baseUrl + "/accounts/{id}/balance", accountIdOrNumber)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (resp != null) {
                Long accountId = resp.get("accountId") != null ? ((Number) resp.get("accountId")).longValue() : null;
                String accNum = (String) resp.get("accountNumber");
                String accType = (String) resp.get("accountType");
                String curr = (String) resp.get("currency");
                BigDecimal current = resp.get("currentBalance") != null ? new BigDecimal(resp.get("currentBalance").toString()) : BigDecimal.ZERO;
                BigDecimal held = resp.get("heldBalance") != null ? new BigDecimal(resp.get("heldBalance").toString()) : BigDecimal.ZERO;
                BigDecimal avail = resp.get("availableBalance") != null ? new BigDecimal(resp.get("availableBalance").toString()) : BigDecimal.ZERO;
                String status = (String) resp.get("status");

                return Optional.of(new CoreBalance(accountId, accNum, accType, curr, current, held, avail, status));
            }
        } catch (Exception e) {
            log.warn("[t24-account-client] Live balance inquiry failed for {}: {}", accountIdOrNumber, e.getMessage());
        }
        return Optional.empty();
    }
}
