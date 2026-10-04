package com.bank.transaction;

import com.bank.transaction.client.RiskEngineClient;
import com.bank.transaction.client.T24AdapterClient;
import com.bank.transaction.controller.RemittanceController;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.dto.RiskResult;
import com.bank.transaction.dto.T24Result;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.RemittanceRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.RemittanceLedgerService;
import com.bank.transaction.service.RemittanceOrchestratorService;
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
import java.util.List;
import java.util.Map;

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
                ledgerService,
                riskEngineClient,
                t24AdapterClient,
                redisTemplate,
                objectMapper
        );

        controller = new RemittanceController(orchestratorService);
    }

    @Test
    @DisplayName("Missing X-Auth-Customer-Id header throws 401 Unauthorized")
    void missingAuthHeader_throws401() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> controller.processRemittance(request, null, null, null))
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
    @DisplayName("Fraud risk score > 0.85 throws 422 before holding funds")
    void fraudRiskScore_throws422BeforeHold() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("1");
        request.setTargetAccountId("2");
        request.setAmount(new BigDecimal("500.00"));

        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.95"), "REJECT", List.of("High risk velocity")));

        assertThatThrownBy(() -> orchestratorService.processRemittance(request, "key-2", "corr-2", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Verify zero DB updates occurred for holding funds
        verify(jdbcTemplate, never()).update(startsWith("UPDATE dbo.ACCOUNT SET held_balance"), any(), any(), any());
    }
}
