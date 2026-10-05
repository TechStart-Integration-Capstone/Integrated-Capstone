package com.bank.transaction;

import com.bank.transaction.client.RiskEngineClient;
import com.bank.transaction.client.T24AdapterClient;
import com.bank.transaction.controller.InternalTransferController;
import com.bank.transaction.dto.InternalTransferRequest;
import com.bank.transaction.dto.InternalTransferResponse;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RiskResult;
import com.bank.transaction.dto.T24Result;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.model.TransactionRecord;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.RemittanceRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.RemittanceLedgerService;
import com.bank.transaction.service.RemittanceOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InternalTransferTest {

    private static final String BANK_ACCOUNT = "PH1000000LOAN";
    private static final String CUSTOMER_ACCOUNT = "001133218709";

    private RemittanceRepository remittanceRepository;
    private TransactionRepository transactionRepository;
    private OutboxEventRepository outboxEventRepository;
    private RiskEngineClient riskEngineClient;
    private T24AdapterClient t24AdapterClient;
    private JdbcTemplate jdbcTemplate;
    private RemittanceOrchestratorService orchestratorService;
    private InternalTransferController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        remittanceRepository = mock(RemittanceRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        riskEngineClient = mock(RiskEngineClient.class);
        t24AdapterClient = mock(T24AdapterClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        outboxEventRepository = mock(OutboxEventRepository.class);
        RemittanceLedgerService ledgerService = new RemittanceLedgerService(
                remittanceRepository, transactionRepository, outboxEventRepository, jdbcTemplate);
        orchestratorService = new RemittanceOrchestratorService(
                remittanceRepository, ledgerService, riskEngineClient, t24AdapterClient, redisTemplate, new ObjectMapper());
        controller = new InternalTransferController(orchestratorService);

        // Bank loan pool (account 10, owned by customer 99) and a customer savings account (account 4, customer 2)
        stubAccount(10L, 99L, BANK_ACCOUNT, "50000000.00");
        stubAccount(4L, 2L, CUSTOMER_ACCOUNT, "84320.50");
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);
        when(remittanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TransactionRecord savedTx = mock(TransactionRecord.class);
        when(savedTx.getTransactionId()).thenReturn(123L);
        when(transactionRepository.save(any())).thenReturn(savedTx);
        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("POSTED", "FT26278ABC12", "FT26278ABC12/1", null, false));
    }

    private void stubAccount(Long id, Long customerId, String number, String balance) {
        List<Map<String, Object>> row = List.of(Map.of(
                "account_id", id, "customer_id", customerId, "account_number", number,
                "current_balance", new BigDecimal(balance), "held_balance", BigDecimal.ZERO));
        when(jdbcTemplate.queryForList(anyString(), eq(number), eq(number))).thenReturn(row);
        when(jdbcTemplate.queryForList(anyString(), eq(String.valueOf(id)), eq(String.valueOf(id)))).thenReturn(row);
    }

    private InternalTransferRequest request(String source, String target, String amount, String type, String key) {
        InternalTransferRequest r = new InternalTransferRequest();
        r.setSourceAccountNo(source);
        r.setTargetAccountNo(target);
        r.setAmount(new BigDecimal(amount));
        r.setTransactionType(type);
        r.setIdempotencyKey(key);
        return r;
    }

    @Test
    @DisplayName("Internal endpoint without X-Internal-Service header returns 403")
    void missingInternalHeader_throws403() {
        InternalTransferRequest req = request(BANK_ACCOUNT, CUSTOMER_ACCOUNT, "250000.00", "LOAN_DISBURSEMENT", "LOAN-DISB-LAP-1");

        assertThatThrownBy(() -> controller.transfer(null, "corr-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> controller.transfer("some-other-service", "corr-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(t24AdapterClient);
    }

    @Test
    @DisplayName("LOAN_DISBURSEMENT from the bank account skips the risk engine and posts")
    void disbursement_skipsRiskEngine() {
        InternalTransferResponse response = controller.transfer("loan-service", "corr-1",
                request(BANK_ACCOUNT, CUSTOMER_ACCOUNT, "250000.00", "LOAN_DISBURSEMENT", "LOAN-DISB-LAP-20261005-000014")).getBody();

        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getTransactionId()).isEqualTo(123L);
        assertThat(response.getFtReference()).isEqualTo("FT26278ABC12");
        verify(riskEngineClient, never()).evaluateRisk(any(), any(), any(), any(), any());

        ArgumentCaptor<TransactionRecord> ledgerRow = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository).save(ledgerRow.capture());
        ArgumentCaptor<Remittance> saved = ArgumentCaptor.forClass(Remittance.class);
        verify(remittanceRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo("LOAN_DISBURSEMENT");
        assertThat(saved.getValue().getCallerCustomerId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Outbox event carries the audit fields (transactionId, debit leg, balances) so the transfer reconciles")
    void outboxPayload_hasAuditFields() throws Exception {
        controller.transfer("loan-service", "corr-6",
                request(BANK_ACCOUNT, CUSTOMER_ACCOUNT, "250000.00", "LOAN_DISBURSEMENT", "LOAN-DISB-LAP-3"));

        ArgumentCaptor<com.bank.transaction.model.OutboxEvent> event = ArgumentCaptor.forClass(com.bank.transaction.model.OutboxEvent.class);
        verify(outboxEventRepository).save(event.capture());
        var payload = new ObjectMapper().readTree(event.getValue().getPayload());
        assertThat(payload.get("transactionId").asLong()).isEqualTo(123L);
        assertThat(payload.get("accountId").asLong()).isEqualTo(10L);
        assertThat(payload.get("customerId").asLong()).isEqualTo(99L);
        assertThat(payload.get("operation").asText()).isEqualTo("DEBIT");
        assertThat(payload.get("transactionType").asText()).isEqualTo("LOAN_DISBURSEMENT");
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("250000.00");
        assertThat(payload.get("beforeBalance").decimalValue()).isEqualByComparingTo("50000000.00");
        assertThat(payload.get("afterBalance").decimalValue()).isEqualByComparingTo("49750000.00");
    }

    @Test
    @DisplayName("LOAN_REPAYMENT runs the normal risk screening")
    void repayment_callsRiskEngine() {
        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "APPROVE", List.of()));

        InternalTransferResponse response = controller.transfer("loan-service", "corr-2",
                request(CUSTOMER_ACCOUNT, BANK_ACCOUNT, "9038.10", "LOAN_REPAYMENT", "LOAN-REPAY-key-1")).getBody();

        assertThat(response.getStatus()).isEqualTo("POSTED");
        // Numeric account ids, not account numbers: the risk engine rejects "PH1000000LOAN" as a non-integer.
        verify(riskEngineClient).evaluateRisk(eq(4L), eq(10L), any(), any(), any());
    }

    @Test
    @DisplayName("LOAN_REPAYMENT with too little balance is REJECTED with INSUFFICIENT_FUNDS")
    void repayment_insufficientFunds_rejected() {
        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "APPROVE", List.of()));

        InternalTransferResponse response = controller.transfer("loan-service", "corr-3",
                request(CUSTOMER_ACCOUNT, BANK_ACCOUNT, "90000.00", "LOAN_REPAYMENT", "LOAN-REPAY-key-2")).getBody();

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getReason()).isEqualTo(InternalTransferResponse.REASON_INSUFFICIENT_FUNDS);
        verifyNoInteractions(t24AdapterClient);
    }

    @Test
    @DisplayName("T24 still processing is reported as PENDING_CORE and is not cached as final")
    void t24Processing_reportsPendingCore() {
        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("PROCESSING", null, null, "T24 SLA processing delay", false));

        InternalTransferResponse response = controller.transfer("loan-service", "corr-4",
                request(BANK_ACCOUNT, CUSTOMER_ACCOUNT, "250000.00", "LOAN_DISBURSEMENT", "LOAN-DISB-LAP-2")).getBody();

        assertThat(response.getStatus()).isEqualTo("PENDING_CORE");
        assertThat(response.getTransactionId()).isNull();
    }

    @Test
    @DisplayName("The public transfer endpoint always runs as TRANSFER with risk screening")
    void publicTransfer_cannotSkipRisk() {
        when(riskEngineClient.evaluateRisk(any(), any(), any(), any(), any()))
                .thenReturn(new RiskResult(new BigDecimal("0.10"), "APPROVE", List.of()));
        RemittanceRequest req = new RemittanceRequest(CUSTOMER_ACCOUNT, BANK_ACCOUNT, new BigDecimal("100.00"), "PHP");
        req.setTransactionType("LOAN_DISBURSEMENT");

        orchestratorService.processRemittance(req, "public-key-1", "corr-5", 2L);

        verify(riskEngineClient).evaluateRisk(any(), any(), any(), any(), any());
        ArgumentCaptor<Remittance> saved = ArgumentCaptor.forClass(Remittance.class);
        verify(remittanceRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo("TRANSFER");
    }
}
