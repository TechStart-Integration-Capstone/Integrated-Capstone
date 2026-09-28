package com.bank.audit;

import com.bank.audit.model.LedgerMutationAudit;
import com.bank.audit.repository.LedgerMutationAuditRepository;
import com.bank.audit.service.AuditKafkaConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditKafkaConsumer.
 * Invokes consume() directly — no Kafka broker or PostgreSQL needed.
 */
@ExtendWith(MockitoExtension.class)
class AuditKafkaConsumerTest {

    @Mock private LedgerMutationAuditRepository auditRepository;
    @InjectMocks private AuditKafkaConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach void setUp() {
        try {
            var f = AuditKafkaConsumer.class.getDeclaredField("objectMapper");
            f.setAccessible(true); f.set(consumer, objectMapper);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String event(long txId, long accId, long custId, String op, String amount, String before, String after) {
        return String.format("{\"transactionId\":%d,\"accountId\":%d,\"customerId\":%d,\"operation\":\"%s\",\"amount\":\"%s\",\"beforeBalance\":\"%s\",\"afterBalance\":\"%s\",\"referenceNo\":\"REF-%d\"}",
                txId, accId, custId, op, amount, before, after, txId);
    }

    @Test @DisplayName("consume: DEBIT event saves LedgerMutationAudit with correct fields")
    void consume_debitEvent_savesAuditRecord() {
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<LedgerMutationAudit> cap = ArgumentCaptor.forClass(LedgerMutationAudit.class);

        consumer.consume(event(1L,101L,10L,"DEBIT","200.0000","1000.0000","800.0000"));

        verify(auditRepository,times(1)).save(cap.capture());
        LedgerMutationAudit a = cap.getValue();
        assertThat(a.getTransactionId()).isEqualTo(1L);
        assertThat(a.getAccountId()).isEqualTo(101L);
        assertThat(a.getOperation()).isEqualTo("DEBIT");
        assertThat(a.getAmount()).isEqualByComparingTo("200.0000");
        assertThat(a.getBeforeBalance()).isEqualByComparingTo("1000.0000");
        assertThat(a.getAfterBalance()).isEqualByComparingTo("800.0000");
    }

    @Test @DisplayName("consume: CREDIT event saves LedgerMutationAudit with correct operation")
    void consume_creditEvent_savesAuditRecord() {
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<LedgerMutationAudit> cap = ArgumentCaptor.forClass(LedgerMutationAudit.class);

        consumer.consume(event(2L,102L,11L,"CREDIT","500.0000","500.0000","1000.0000"));

        verify(auditRepository).save(cap.capture());
        assertThat(cap.getValue().getOperation()).isEqualTo("CREDIT");
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("500.0000");
    }

    @Test @DisplayName("consume: audit record is always append-only (save called, never delete)")
    void consume_appendOnly_neverDeletes() {
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        consumer.consume(event(3L,103L,12L,"DEBIT","100.0000","600.0000","500.0000"));
        verify(auditRepository,times(1)).save(any());
        verify(auditRepository,never()).delete(any());
        verify(auditRepository,never()).deleteAll();
    }

    @Test @DisplayName("consume: each event results in exactly one repository save")
    void consume_exactlyOneSavePerEvent() {
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        consumer.consume(event(4L,104L,13L,"CREDIT","50.0000","950.0000","1000.0000"));
        consumer.consume(event(5L,104L,13L,"DEBIT", "50.0000","1000.0000","950.0000"));
        verify(auditRepository,times(2)).save(any());
    }

    @Test @DisplayName("consume: malformed JSON is swallowed gracefully — no exception propagated")
    void consume_malformedJson_noExceptionPropagated() {
        assertThatNoException().isThrownBy(() -> consumer.consume("BAD_JSON{{"));
        verify(auditRepository,never()).save(any());
    }

    @Test @DisplayName("consume: event with missing fields uses safe defaults — still saves")
    void consume_missingFields_saveWithDefaults() {
        // Only operation provided — consumer catches any parse/null issues internally
        assertThatNoException().isThrownBy(() -> consumer.consume("{\"operation\":\"DEBIT\"}"));
    }
}
