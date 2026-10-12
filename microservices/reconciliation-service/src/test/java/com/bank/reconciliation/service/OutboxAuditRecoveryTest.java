package com.bank.reconciliation.service;

import com.bank.reconciliation.model.oracle.TransactionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OutboxAuditRecoveryTest {
    private final JdbcTemplate ledger = mock(JdbcTemplate.class);
    private final JdbcTemplate audit = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboxAuditRecovery recovery = new OutboxAuditRecovery(ledger, audit, mapper);
    private final TransactionRecord tx = new TransactionRecord(1L, "SUCCESS", new BigDecimal("50"), "REF-1");
    private ObjectNode payload;

    @BeforeEach void setup() throws Exception {
        payload = (ObjectNode) mapper.readTree("""
                {"transactionId":1,"accountId":10,"referenceNo":"REF-1","operation":"DEBIT",
                 "amount":"50.0000","currency":"PHP","beforeBalance":"100","afterBalance":"50"}
                """);
    }

    private OutboxAuditRecovery.Event event(String type, String transactionType) {
        return new OutboxAuditRecovery.Event(7, type, payload.toString(), 10, "PHP", transactionType);
    }

    @SuppressWarnings("unchecked")
    private void events(OutboxAuditRecovery.Event... values) {
        when(ledger.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of(values));
    }

    @Test void restoresOriginalDebitAndDoesNotChangeOutboxOrBalances() {
        events(event("REMITTANCE_COMPLETED", "P2P_REMITTANCE"));
        when(audit.update(anyString(), any(Object[].class))).thenReturn(1);
        assertThat(recovery.recover(tx)).isTrue();
        verify(audit).update(contains("ON CONFLICT (transaction_id, account_id) DO NOTHING"),
                eq(1L), eq(10L), eq("DEBIT"), eq(new BigDecimal("50.0000")), eq("PHP"),
                eq(new BigDecimal("100.0000")), eq(new BigDecimal("50.0000")), eq(1L));
        verify(ledger, never()).update(anyString(), any(Object[].class));
    }

    @Test void restoresInterestCreditFromOriginalBalances() {
        payload.put("operation", "CREDIT").put("afterBalance", "150").put("transactionType", "INTEREST_CREDIT");
        events(event("TRANSACTION_SUCCESS", "INTEREST_CREDIT"));
        when(audit.update(anyString(), any(Object[].class))).thenReturn(1);
        assertThat(recovery.recover(tx)).isTrue();
    }

    @Test void concurrentOrRepeatedInsertIsNotCountedAsRecovery() {
        events(event("TRANSACTION_SUCCESS", "TRANSFER_OUT"));
        when(audit.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThat(recovery.recover(tx)).isFalse();
    }

    @Test void identicalDuplicateOutboxEventsProduceOnlyOneInsert() {
        events(event("TRANSACTION_SUCCESS", "TRANSFER_OUT"), event("TRANSACTION_SUCCESS", "TRANSFER_OUT"));
        recovery.recover(tx);
        verify(audit, times(1)).update(anyString(), any(Object[].class));
    }

    @Test void conflictingBalancesNeverProduceAnAuditRow() {
        var original = event("TRANSACTION_SUCCESS", "TRANSFER_OUT");
        payload.put("beforeBalance", "200").put("afterBalance", "150");
        events(original, event("TRANSACTION_SUCCESS", "TRANSFER_OUT"));
        assertThat(recovery.recover(tx)).isFalse();
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings={"transactionId","accountId","referenceNo","operation","amount","currency","beforeBalance","afterBalance"})
    void incompleteEventNeverInventsDefaults(String missing) {
        payload.remove(missing);
        events(event("TRANSACTION_SUCCESS", "TRANSFER_OUT"));
        assertThat(recovery.recover(tx)).isFalse();
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings={"transactionId","accountId","referenceNo","amount","currency","afterBalance","status","transactionType","operation"})
    void inconsistentEvidenceStaysMissing(String field) {
        payload.put(field, switch (field) {
            case "transactionId", "accountId" -> "999";
            case "amount", "afterBalance" -> "49";
            case "currency" -> "USD";
            case "status" -> "FAILED";
            default -> "INVALID";
        });
        events(event("REMITTANCE_COMPLETED", "P2P_REMITTANCE"));
        assertThat(recovery.recover(tx)).isFalse();
        verifyNoInteractions(audit);
    }

    @Test void absentOutboxRemainsMissing() {
        events();
        assertThat(recovery.recover(tx)).isFalse();
        verifyNoInteractions(audit);
    }

    @Test void unsuccessfulLedgerIsNotRecovered() {
        assertThat(recovery.recover(new TransactionRecord(1L, "FAILED", tx.getAmount(), "REF-1"))).isFalse();
        verifyNoInteractions(ledger, audit);
    }

    @Test void databaseFailureIsNotReportedAsSuccess() {
        events(event("TRANSACTION_SUCCESS", "TRANSFER_OUT"));
        when(audit.update(anyString(), any(Object[].class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> recovery.recover(tx)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
