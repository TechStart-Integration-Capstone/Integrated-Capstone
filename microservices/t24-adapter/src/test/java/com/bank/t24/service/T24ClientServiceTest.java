package com.bank.t24.service;

import com.bank.t24.controller.T24AdapterController;
import com.bank.t24.dto.T24TransferRequest;
import com.bank.t24.dto.T24TransferResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class T24ClientServiceTest {
    private static final String POSTED =
            "{\"status\":\"POSTED\",\"ftReference\":\"FT123\",\"ofsResponse\":\"FT123/1\"}";
    private HttpServer server;
    private T24IdempotencyStore store;
    private T24AdapterController adapter;
    private String contentType = "application/json";
    private String responseBody = POSTED;
    private int httpStatus = 200;
    private final AtomicInteger calls = new AtomicInteger();
    private String accept;
    private String sentBody;
    private final T24TransferRequest request =
            new T24TransferRequest("TX-TEST", "1000100001", "1000100002", new BigDecimal("222.00"), "PHP");

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ofs/process", exchange -> {
            calls.incrementAndGet();
            accept = exchange.getRequestHeaders().getFirst("Accept");
            sentBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (contentType != null) exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpStatus, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        store = new T24IdempotencyStore();
        adapter = new T24AdapterController(new T24ClientService(new OfsFormatterService(), store,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/ofs/process", 1000, 2000));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"application/json", "application/octet-stream", "text/plain"})
    void decodesJsonAcrossContentTypesAndReplaysWithoutReposting(String type) {
        contentType = type;
        ResponseEntity<T24TransferResponse> result = adapter.processTransfer(request, null);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getStatus()).isEqualTo("POSTED");
        assertThat(result.getBody().getFtReference()).isEqualTo("FT123");
        assertThat(accept).isEqualTo("application/json");
        assertThat(sentBody).contains("\"referenceNo\":\"TX-TEST\"", "AMOUNT::222.00");
        assertThat(adapter.processTransfer(request, null).getBody().isCachedResponse()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/octet-stream"})
    void confirmedRejectionStillReturns422WithTheCoreReason(String type) {
        contentType = type;
        responseBody = "{\"status\":\"REJECTED\",\"ftReference\":\"FT123\","
                + "\"ofsResponse\":\"FT123/-1\",\"reason\":\"Credit account is FROZEN\"}";
        ResponseEntity<T24TransferResponse> result = adapter.processTransfer(request, null);
        assertThat(result.getStatusCode().value()).isEqualTo(422);
        assertThat(result.getBody().getReason()).isEqualTo("Credit account is FROZEN");
        assertThat(store.get(request.getReferenceNo())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not JSON", "<html>Bad gateway</html>", "null", "[]", "{}",
            "{\"status\":17}", "{\"status\":\"PROCESSING\"}", "{\"status\":\"UNKNOWN\"}",
            "{\"status\":\"POSTED\"}", "{\"status\":\"REJECTED\"}",
            "{\"status\":\"POSTED\",\"ftReference\":123,\"ofsResponse\":\"123/1\"}",
            "{\"status\":\"REJECTED\",\"ftReference\":\"FT123\",\"ofsResponse\":\"FT123/1\"}",
            "{\"status\":\"POSTED\",\"ftReference\":\"FT123\",\"ofsResponse\":\"FT123/-1\"}",
            "{\"status\":\"POSTED\",\"ftReference\":\"FT123\",\"ofsResponse\":\"FT123/1\"} {}"})
    void unconfirmedResponsesStayPendingAndCanRecoverWithTheSameReference(String body) {
        contentType = "application/octet-stream";
        responseBody = body;
        ResponseEntity<T24TransferResponse> result = adapter.processTransfer(request, null);
        assertThat(result.getStatusCode().value()).isEqualTo(202);
        assertThat(result.getBody().getStatus()).isEqualTo("PROCESSING");
        assertThat(store.get(request.getReferenceNo())).isEmpty();

        responseBody = POSTED;
        assertThat(adapter.processTransfer(request, null).getBody().getStatus()).isEqualTo("POSTED");
        assertThat(calls.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 422, 500, 503})
    void httpErrorsAreNotEvidenceOfARejectedPosting(int status) {
        httpStatus = status;
        responseBody = "{\"error\":\"upstream failure\"}";
        assertThat(adapter.processTransfer(request, null).getStatusCode().value()).isEqualTo(202);
        assertThat(store.get(request.getReferenceNo())).isEmpty();
    }

    @Test
    void connectionFailureStaysPending() {
        server.stop(0);
        assertThat(adapter.processTransfer(request, null).getStatusCode().value()).isEqualTo(202);
    }
}

