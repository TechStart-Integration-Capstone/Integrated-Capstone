package com.bank.t24.service;

import com.bank.t24.dto.T24TransferRequest;
import com.bank.t24.dto.T24TransferResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            // Read bytes so JSON with a missing/generic Content-Type is still decoded.
            // A transport or decoding failure does not prove the core rejected the posting.
            byte[] responseBody = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);

            if (responseBody == null || responseBody.length == 0) {
                return T24TransferResponse.timeout("Empty T24 response; posting outcome is unconfirmed");
            }

            JsonNode responseMap = objectMapper.reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(responseBody);
            String status = textField(responseMap, "status");
            String ftReference = textField(responseMap, "ftReference");
            String rawOfs = textField(responseMap, "ofsResponse");
            String reason = textField(responseMap, "reason");

            T24TransferResponse response;
            if ("POSTED".equalsIgnoreCase(status) && ftReference != null
                    && (ftReference + "/1").equals(rawOfs)) {
                response = T24TransferResponse.success(ftReference, rawOfs, false);
                idempotencyStore.put(request.getReferenceNo(), response);
            } else if ("REJECTED".equalsIgnoreCase(status) && ftReference != null
                    && (ftReference + "/-1").equals(rawOfs)) {
                response = T24TransferResponse.rejected(ftReference, rawOfs, reason != null ? reason : "T24 rejection /-1");
            } else {
                response = T24TransferResponse.timeout("T24 posting outcome is unconfirmed; awaiting recovery");
            }
            return response;

        } catch (ResourceAccessException e) {
            log.warn("[t24-adapter] T24 Simulator read timeout or connection failure: {}", e.getMessage());
            return T24TransferResponse.timeout("T24 Core Banking SLA timeout (2s exceeded)");
        } catch (Exception e) {
            log.error("[t24-adapter] Unexpected error calling T24 Simulator: {}", e.getMessage(), e);
            return T24TransferResponse.timeout("T24 response could not be confirmed; awaiting recovery");
        }
    }

    private static String textField(JsonNode response, String field) {
        JsonNode value = response == null ? null : response.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue() : null;
    }
}
