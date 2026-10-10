package com.bank.auth.banking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

/** Private-network call: the persisted instruction is the only input to settlement. */
@Component
public class ExternalSettlementClient {
    private final RestClient client;

    public ExternalSettlementClient(@Value("${app.orchestrator.url:http://transaction-service:8083}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public void settle(String reference, long customerId) {
        client.post().uri("/internal/remittance/external")
                .header("X-Internal-Service", "auth-service")
                .body(Map.of("reference", reference, "customerId", customerId))
                .retrieve().toBodilessEntity();
    }
}
