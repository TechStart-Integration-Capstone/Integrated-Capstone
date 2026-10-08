package com.bank.transaction;

import com.bank.transaction.client.RiskEngineClient;
import com.bank.transaction.client.T24AdapterClient;
import com.bank.transaction.controller.RemittanceController;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.dto.RiskResult;
import com.bank.transaction.dto.T24Result;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.model.TransactionRecord;
import org.springframework.test.util.ReflectionTestUtils;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.RemittanceRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.RemittanceLedgerService;
import com.bank.transaction.service.RemittanceOrchestratorService;
import com.bank.transaction.service.RiskDecisionPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.bank.transaction.service.RemittanceSagaWorker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemittanceSagaTest {

    private RemittanceRepository remittanceRepository;
    private TransactionRepository transactionRepository;
    private OutboxEventRepository outboxEventRepository;
    private RiskEngineClient riskEngineClient;
    private T24AdapterClient t24AdapterClient;
    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;

    private RemittanceLedgerService ledgerService;
    private RemittanceOrchestratorService orchestratorService;
    private RemittanceController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        remittanceRepository = mock(RemittanceRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        riskEngineClient = mock(RiskEngineClient.class);
        t24AdapterClient = mock(T24AdapterClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        ledgerService = new RemittanceLedgerService(
                remittanceRepository,
                transactionRepository,
                outboxEventRepository,
                jdbcTemplate
        );

        orchestratorService = new RemittanceOrchestratorService(
                remittanceRepository,
                ledgerService,
                riskEngineClient,
                t24AdapterClient,
                redisTemplate,
                objectMapper,
                new RiskDecisionPublisher(mock(org.springframework.kafka.core.KafkaTemplate.class), objectMapper)
        );

        controller = new RemittanceController(orchestratorService);
    }

    @Test
    @DisplayName("Missing Idempotency-Key header throws 400 Bad Request")
    void missingIdempotencyKey_throws400() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> controller.processRemittance(request, null, "corr-1", "1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Missing X-Auth-Customer-Id header throws 401 Unauthorized")
    void missingAuthHeader_throws401() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> controller.processRemittance(request, "idemp-1", "corr-1", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Caller customer ID mismatch (ownership check) throws 403 Forbidden")
    void callerOwnershipMismatch_throws403() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        // Source account 1 belongs to customerId 999
        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L,
                        "customer_id", 999L,
                        "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"),
                        "held_balance", BigDecimal.ZERO
                )));

        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L,
                        "customer_id", 888L,
                        "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"),
                        "held_balance", BigDecimal.ZERO
                )));

        // Risk passes
        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        // Caller is customerId 888 (mismatch!)
        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "key-1", "corr-1", 888L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Same-account self transfer throws 400 Bad Request")
    void selfTransfer_throws400() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("1"); // Same account!
        request.setAmount(new BigDecimal("100.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L,
                        "customer_id", 1L,
                        "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"),
                        "held_balance", BigDecimal.ZERO
                )));

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "key-self", "corr-1", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Fraud risk score > 0.85 throws 422 before holding funds")
    void fraudRiskScore_throws422BeforeHold() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("500.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));
        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.95"), "REJECT", List.of("High risk velocity")));

        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "key-2", "corr-2", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Verify zero DB updates occurred for holding funds
        verify(jdbcTemplate, never()).update(startsWith("UPDATE dbo.ACCOUNT SET held_balance"), any(), any(), any());
    }

    @Test
    @DisplayName("T24 timeout returns 202 Accepted with status PROCESSING")
    void t24Timeout_returns202Processing() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        Remittance rem = new Remittance("TX-PH-123", 1L, 2L, new BigDecimal("100.00"), "PHP", "PENDING_CORE");
        when(remittanceRepository.save(any())).thenReturn(rem);

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("PROCESSING", null, null, "T24 SLA processing delay", false));

        ResponseEntity<RemittanceResponse> responseEntity = controller.processRemittance(request, "idemp-timeout", "corr-1", "1");

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(responseEntity.getBody().getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("Post-T24 commit failure preserves T24_POSTED status for saga worker without releasing funds")
    void postT24CommitFailure_preservesT24PostedStatus() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        Remittance rem = new Remittance("TX-PH-999", 1L, 2L, new BigDecimal("100.00"), "PHP", "PENDING_CORE");
        rem.setCallerCustomerId(1L);
        when(remittanceRepository.save(any())).thenReturn(rem);

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("POSTED", "FT2600100999", "TX-PH-999", null, false));

        // Mock DB failure during commitLedgerMutation
        doThrow(new RuntimeException("DB Outbox write error")).when(transactionRepository).save(any());

        ResponseEntity<RemittanceResponse> responseEntity = controller.processRemittance(request, "idemp-commit-fail", "corr-1", "1");

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(responseEntity.getBody().getStatus()).isEqualTo("PROCESSING");
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(responseEntity.getBody().getStatus()).isEqualTo("PROCESSING");
        // Verify held balance was NOT released (releaseHoldFunds SQL uses CASE WHEN held_balance)
        verify(jdbcTemplate, never()).update(contains("CASE WHEN held_balance"), any(), any());
    }

    @Test
    @DisplayName("Legitimate retry with identical details returns original response")
    void retryWithIdenticalDetails_returnsOriginalResponse() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));

        Remittance existing = new Remittance("TX-PH-EXIST", 1L, 2L, new BigDecimal("100.00"), "PHP", "POSTED");
        existing.setCallerCustomerId(1L);
        existing.setFtReference("FT2600100777");

        when(remittanceRepository.findByCallerCustomerIdAndIdempotencyKey(1L, "valid-retry-key"))
                .thenReturn(java.util.Optional.of(existing));

        RemittanceResponse response = orchestratorService.processRemittance(request, "valid-retry-key", "corr-1", 1L);

        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getReferenceNo()).isEqualTo("TX-PH-EXIST");
        assertThat(response.getFtReference()).isEqualTo("FT2600100777");
        assertThat(response.isCachedIdempotentResponse()).isTrue();
    }

    @Test
    @DisplayName("Reusing idempotency key with different request details throws 409 Conflict")
    void retryWithDifferentDetails_throws409Conflict() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));

        // Existing remittance has different amount (200.00 vs 100.00)
        Remittance existing = new Remittance("TX-PH-EXIST", 1L, 2L, new BigDecimal("200.00"), "PHP", "POSTED");
        existing.setCallerCustomerId(1L);
        when(remittanceRepository.findByCallerCustomerIdAndIdempotencyKey(1L, "reused-key"))
                .thenReturn(java.util.Optional.of(existing));

        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "reused-key", "corr-1", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Exceeding per-transaction limit throws 422 and records LIMIT_CHECK failure")
    void perTransactionLimitExceeded_throws422() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("30000.00")); // exceeds default 25,000.00

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("100000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "limit-key-1", "corr-1", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds per-transaction limit")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(remittanceRepository, atLeastOnce()).save(argThat(r ->
                Remittance.STATUS_FAILED.equals(r.getStatus()) &&
                Remittance.STEP_LIMIT_CHECK.equals(r.getInternalStatus())
        ));
    }

    @Test
    @DisplayName("Exceeding daily cumulative limit throws 422 and records LIMIT_CHECK failure")
    void dailyLimitExceeded_throws422() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("20000.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("100000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        // Cumulative today is already 40,000; + 20,000 exceeds 50,000 daily limit
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(1L)))
                .thenReturn(new BigDecimal("40000.00"));

        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "daily-limit-key", "corr-1", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Daily transfer limit")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(remittanceRepository, atLeastOnce()).save(argThat(r ->
                Remittance.STATUS_FAILED.equals(r.getStatus()) &&
                Remittance.STEP_LIMIT_CHECK.equals(r.getInternalStatus())
        ));
    }

    @Test
    @DisplayName("Insufficient funds check throws 422 and records FUNDS_CHECK failure")
    void insufficientFunds_throws422() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("5000.00"));

        // Account only has 1,000 balance
        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "funds-key", "corr-1", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient available funds")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(remittanceRepository, atLeastOnce()).save(argThat(r ->
                Remittance.STATUS_FAILED.equals(r.getStatus()) &&
                Remittance.STEP_FUNDS_CHECK.equals(r.getInternalStatus())
        ));
    }

    @Test
    @DisplayName("Direct dispatch bypasses client cancel window and executes core banking immediately")
    void directDispatch_withoutClientCancelWindow() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));
        request.setCancelWindowSeconds(30);

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        Remittance savedRemittance = new Remittance("TX-PH-WIN", 1L, 2L, new BigDecimal("100.00"), "PHP", Remittance.STATUS_RESERVED);
        when(remittanceRepository.save(any(Remittance.class))).thenReturn(savedRemittance);

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("POSTED", "FT202610080001", null, null, false));

        when(transactionRepository.save(any())).thenAnswer(invocation -> {
            TransactionRecord tx = invocation.getArgument(0);
            ReflectionTestUtils.setField(tx, "transactionId", 123L);
            return tx;
        });

        ResponseEntity<RemittanceResponse> responseEntity = controller.processRemittance(request, "idemp-window", "corr-win", "1");

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        RemittanceResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualToIgnoringCase(Remittance.STATUS_POSTED);

        // Verify T24 adapter WAS invoked immediately without client cancellation delay
        verify(t24AdapterClient).executeTransfer(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cancel within 30s releases held funds and sets status to Cancelled")
    void cancelWithin30s_releasesHeldFundsAndCancels() {
        Remittance remittance = new Remittance("TX-PH-CANCEL", 1L, 2L, new BigDecimal("100.00"), "PHP", Remittance.STATUS_RESERVED);
        remittance.setInternalStatus(Remittance.INTERNAL_CLIENT_CANCEL_WINDOW);
        remittance.setCallerCustomerId(1L);
        remittance.setCancelUntil(LocalDateTime.now().plusSeconds(25));

        when(remittanceRepository.findByReferenceNo("TX-PH-CANCEL")).thenReturn(Optional.of(remittance));
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);
        when(jdbcTemplate.update(contains("SET internal_status = ?"), any(), any(), any(), any(), any())).thenReturn(1); // window claimed

        ResponseEntity<Map<String, Object>> response = controller.cancelRemittance("TX-PH-CANCEL", "1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(Remittance.STATUS_CANCELLED);
        assertThat(remittance.getStatus()).isEqualTo(Remittance.STATUS_CANCELLED);
        assertThat(remittance.getInternalStatus()).isEqualTo(Remittance.INTERNAL_CANCELLED_BY_USER);

        // Verify hold release SQL was executed
        verify(jdbcTemplate).update(contains("CASE WHEN held_balance >= ?"), eq(new BigDecimal("100.00")), eq(new BigDecimal("100.00")), eq(1L));

        // Verify outbox event REMITTANCE_CANCELLED was recorded
        verify(outboxEventRepository).save(argThat(event -> "REMITTANCE_CANCELLED".equals(event.getEventType())));
    }

    @Test
    @DisplayName("Cancelling after the 30s window expires throws 409 Conflict")
    void cancelAfterExpiration_throws409Conflict() {
        Remittance remittance = new Remittance("TX-PH-EXPIRED", 1L, 2L, new BigDecimal("100.00"), "PHP", Remittance.STATUS_RESERVED);
        remittance.setInternalStatus(Remittance.INTERNAL_CLIENT_CANCEL_WINDOW);
        remittance.setCallerCustomerId(1L);
        remittance.setCancelUntil(LocalDateTime.now().minusSeconds(5)); // Expired!

        when(remittanceRepository.findByReferenceNo("TX-PH-EXPIRED")).thenReturn(Optional.of(remittance));

        assertThatThrownBy(() -> controller.cancelRemittance("TX-PH-EXPIRED", "1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // Ensure hold was NOT released
        verify(jdbcTemplate, never()).update(contains("CASE WHEN held_balance >= ?"), any(), any(), any());
    }

    @Test
    @DisplayName("Deterministic core bank rejection (T24 /-1) triggers instant reversal with 0 retries")
    void t24Reject_performsInstantReversalWithZeroRetries() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        when(jdbcTemplate.queryForList(anyString(), eq("1"), eq("1")))
                .thenReturn(List.of(Map.of(
                        "account_id", 1L, "customer_id", 1L, "account_number", "ACC-PH-1001",
                        "current_balance", new BigDecimal("1000.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("2"), eq("2")))
                .thenReturn(List.of(Map.of(
                        "account_id", 2L, "customer_id", 2L, "account_number", "ACC-PH-2002",
                        "current_balance", new BigDecimal("500.00"), "held_balance", BigDecimal.ZERO
                )));
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        Remittance rem = new Remittance("TX-PH-REJECT", 1L, 2L, new BigDecimal("100.00"), "PHP", Remittance.STATUS_RESERVED);
        rem.setCallerCustomerId(1L);
        when(remittanceRepository.save(any(Remittance.class))).thenReturn(rem);

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "ALLOW", List.of()));

        // T24 returns REJECTED (e.g. invalid target account, T24 /-1 code)
        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("REJECTED", null, null, "Account balance exceeded /-1", false));

        assertThatThrownBy(() -> controller.processRemittance(request, "idemp-reject", "corr-rej", "1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Verify hold was released immediately
        verify(jdbcTemplate).update(contains("CASE WHEN held_balance >= ?"), eq(new BigDecimal("100.00")), eq(new BigDecimal("100.00")), eq(1L));

        // Verify outbox reversal event was written
        verify(outboxEventRepository).save(argThat(event -> "REMITTANCE_REVERSED".equals(event.getEventType())));

        // Verify remittance marked with INTERNAL_T24_REJECTED
        verify(remittanceRepository, atLeastOnce()).save(argThat(r ->
                Remittance.INTERNAL_T24_REJECTED.equals(r.getInternalStatus())
        ));
    }

    @Test
    @DisplayName("Saga worker automatically reverses held funds when max retries (3) exceeded")
    void sagaWorker_autoReversesWhenMaxRetriesExceeded() {
        RemittanceSagaWorker worker = new RemittanceSagaWorker(remittanceRepository, ledgerService, t24AdapterClient);

        Remittance remittance = new Remittance("TX-PH-MAX-RETRIES", 1L, 2L, new BigDecimal("100.00"), "PHP", Remittance.STATUS_PROCESSING);
        remittance.setCallerCustomerId(1L);
        remittance.setRetryCount(3);
        remittance.setMaxRetries(3);
        remittance.setNextRetryAt(LocalDateTime.now().minusSeconds(10));

        when(remittanceRepository.findByStatus("PROCESSING")).thenReturn(List.of(remittance));
        when(remittanceRepository.findByStatus(Remittance.STATUS_PROCESSING)).thenReturn(List.of());
        when(remittanceRepository.findByStatus("PENDING_CORE")).thenReturn(List.of());
        when(remittanceRepository.findByStatus("T24_POSTED")).thenReturn(List.of());
        when(remittanceRepository.findByStatusAndInternalStatusAndCancelUntilBefore(any(), any(), any())).thenReturn(List.of());

        worker.resolvePendingSagas();

        // T24 executeTransfer should NOT be called since retries are exhausted
        verify(t24AdapterClient, never()).executeTransfer(any(), any(), any(), any(), any(), any());

        // Verify hold funds released
        verify(jdbcTemplate).update(contains("CASE WHEN held_balance >= ?"), eq(new BigDecimal("100.00")), eq(new BigDecimal("100.00")), eq(1L));

        // Verify Outbox event recorded
        verify(outboxEventRepository).save(argThat(event -> "REMITTANCE_REVERSED".equals(event.getEventType())));

        // Verify remittance marked as AUTO_REVERSED and STATUS_FAILED
        verify(remittanceRepository, atLeastOnce()).save(argThat(r ->
                Remittance.INTERNAL_AUTO_REVERSED.equals(r.getInternalStatus()) &&
                Remittance.STATUS_FAILED.equals(r.getStatus())
        ));
    }
}
