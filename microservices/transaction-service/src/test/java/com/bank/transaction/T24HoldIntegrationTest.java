package com.bank.transaction;

import com.bank.transaction.client.T24HoldClient;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.RemittanceRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.RemittanceLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class T24HoldIntegrationTest {

    private RemittanceRepository remittanceRepository;
    private TransactionRepository transactionRepository;
    private OutboxEventRepository outboxEventRepository;
    private JdbcTemplate jdbcTemplate;
    private T24HoldClient t24HoldClient;

    private RemittanceLedgerService ledgerService;

    @BeforeEach
    void setUp() {
        remittanceRepository = mock(RemittanceRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        t24HoldClient = mock(T24HoldClient.class);

        ledgerService = new RemittanceLedgerService(
                remittanceRepository,
                transactionRepository,
                outboxEventRepository,
                jdbcTemplate,
                t24HoldClient
        );

        when(remittanceRepository.save(any(Remittance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void mockAccountQueries() {
        Map<String, Object> srcRow = Map.of(
                "account_id", 101L,
                "customer_id", 1L,
                "account_number", "ACC101",
                "current_balance", new BigDecimal("5000.00"),
                "held_balance", BigDecimal.ZERO
        );
        Map<String, Object> tgtRow = Map.of(
                "account_id", 102L,
                "customer_id", 2L,
                "account_number", "ACC102",
                "current_balance", new BigDecimal("1000.00"),
                "held_balance", BigDecimal.ZERO
        );

        when(jdbcTemplate.queryForList(anyString(), eq("101"), eq("101"))).thenReturn(List.of(srcRow));
        when(jdbcTemplate.queryForList(anyString(), eq("102"), eq("102"))).thenReturn(List.of(tgtRow));
    }

    @Test
    @DisplayName("holdFunds calls T24HoldClient when available and succeeds")
    void holdFunds_delegatesToT24HoldClient_success() {
        mockAccountQueries();
        when(t24HoldClient.placeHold(eq(101L), eq("ACC101"), eq(new BigDecimal("500.00")), eq("PHP"), eq("REF-123")))
                .thenReturn(new T24HoldClient.HoldResult(true, 999L, "REF-123", "ACTIVE", null));

        RemittanceRequest request = new RemittanceRequest("101", "102", new BigDecimal("500.00"), "PHP");
        Remittance remittance = ledgerService.holdFunds(request, "REF-123", "idemp-1", 1L);

        assertThat(remittance).isNotNull();
        assertThat(remittance.getReferenceNo()).isEqualTo("REF-123");
        assertThat(remittance.getStatus()).isEqualTo(Remittance.STATUS_RESERVED);
        verify(t24HoldClient, times(1)).placeHold(eq(101L), eq("ACC101"), eq(new BigDecimal("500.00")), eq("PHP"), eq("REF-123"));
        // Local SQL update should not be invoked when t24HoldClient succeeds
        verify(jdbcTemplate, never()).update(startsWith("UPDATE dbo.ACCOUNT SET held_balance = held_balance + ?"), any(), any(), any());
    }

    @Test
    @DisplayName("holdFunds throws ResponseStatusException when T24HoldClient rejects hold")
    void holdFunds_delegatesToT24HoldClient_rejectionThrows() {
        mockAccountQueries();
        when(t24HoldClient.placeHold(any(), any(), any(), any(), any()))
                .thenReturn(new T24HoldClient.HoldResult(false, null, "REF-123", "REJECTED", "Account over limit"));

        RemittanceRequest request = new RemittanceRequest("101", "102", new BigDecimal("500.00"), "PHP");

        assertThatThrownBy(() -> ledgerService.holdFunds(request, "REF-123", "idemp-1", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Hold rejected by T24 Core: Account over limit");
    }

    @Test
    @DisplayName("releaseHoldFunds delegates to T24HoldClient releaseHold")
    void releaseHoldFunds_delegatesToT24() {
        Remittance remittance = new Remittance("REF-REL", 101L, 102L, new BigDecimal("500.00"), "PHP", Remittance.STATUS_RESERVED);
        when(t24HoldClient.releaseHold("REF-REL")).thenReturn(true);

        ledgerService.releaseHoldFunds(remittance, 101L, new BigDecimal("500.00"), "T24 Rejected");

        verify(t24HoldClient, times(1)).releaseHold("REF-REL");
        assertThat(remittance.getStatus()).isEqualTo(Remittance.STATUS_FAILED);
    }

    @Test
    @DisplayName("cancelAndReleaseHold delegates to T24HoldClient releaseHold")
    void cancelAndReleaseHold_delegatesToT24() {
        Remittance remittance = new Remittance("REF-CAN", 101L, 102L, new BigDecimal("500.00"), "PHP", Remittance.STATUS_RESERVED);
        when(t24HoldClient.releaseHold("REF-CAN")).thenReturn(true);

        ledgerService.cancelAndReleaseHold(remittance, "User cancelled");

        verify(t24HoldClient, times(1)).releaseHold("REF-CAN");
        assertThat(remittance.getStatus()).isEqualTo(Remittance.STATUS_CANCELLED);
    }
}
