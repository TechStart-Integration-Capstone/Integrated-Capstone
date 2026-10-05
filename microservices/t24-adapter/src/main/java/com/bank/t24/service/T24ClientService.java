package com.bank.t24.service;

import com.bank.t24.dto.T24TransferRequest;
import com.bank.t24.dto.T24TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * Client service that posts formatted OFS messages to the T24 OFS Simulator sidecar.
 * Enforces SLA read-timeout of 2000 ms.
 */
@Service
public class T24ClientService {

    private static final Logger log = LoggerFactory.getLogger(T24ClientService.class);

    private final OfsFormatterService ofsFormatter;
    private final T24IdempotencyStore idempotencyStore;
    private final RestClient restClient;

    public T24ClientService(
            OfsFormatterService ofsFormatter,
            T24IdempotencyStore idempotencyStore,
            @Value("${app.t24.simulator-url:http://localhost:8090/ofs/process}") String simulatorUrl,
            @Value("${app.t24.connect-timeout-ms:1000}") int connectTimeout,
            @Value("${app.t24.read-timeout-ms:2000}") int readTimeout) {

        this.ofsFormatter = ofsFormatter;
        this.idempotencyStore = idempotencyStore;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(simulatorUrl)
                .requestFactory(factory)
                .build();
    }

    public T24TransferResponse executeTransfer(T24TransferRequest request) {
        // Idempotency check: return cached FT if already processed
        Optional<T24TransferResponse> cached = idempotencyStore.get(request.getReferenceNo());
        if (cached.isPresent()) {
            log.info("[t24-adapter] Idempotent hit for referenceNo={}", request.getReferenceNo());
            T24TransferResponse res = cached.get();
            res.setCachedResponse(true);
            return res;
        }

        String ofsMessage = ofsFormatter.buildFundsTransferOfs(request);
        log.info("[t24-adapter] Posting OFS message for ref={}: {}", request.getReferenceNo(), ofsMessage);

        try {
            Map<String, Object> body = Map.of(
                    "referenceNo", request.getReferenceNo(),
                    "ofsMessage", ofsMessage
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (responseMap == null) {
                return T24TransferResponse.rejected(null, ofsMessage, "Empty response from T24 Simulator");
            }

            String status = (String) responseMap.get("status");
            String ftReference = (String) responseMap.get("ftReference");
            String rawOfs = (String) responseMap.get("ofsResponse");
            String reason = (String) responseMap.get("reason");

            T24TransferResponse response;
            if ("POSTED".equalsIgnoreCase(status)) {
                response = T24TransferResponse.success(ftReference, rawOfs, false);
                idempotencyStore.put(request.getReferenceNo(), response);
            } else {
                response = T24TransferResponse.rejected(ftReference, rawOfs, reason != null ? reason : "T24 rejection /-1");
            }
            return response;

        } catch (ResourceAccessException e) {
            log.warn("[t24-adapter] T24 Simulator read timeout or connection failure: {}", e.getMessage());
            return T24TransferResponse.timeout("T24 Core Banking SLA timeout (2s exceeded)");
        } catch (Exception e) {
            log.error("[t24-adapter] Unexpected error calling T24 Simulator: {}", e.getMessage(), e);
            return T24TransferResponse.rejected(null, ofsMessage, "T24 system error: " + e.getMessage());
        }
    }
}
