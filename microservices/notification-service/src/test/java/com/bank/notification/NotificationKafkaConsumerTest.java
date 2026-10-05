package com.bank.notification;

import com.bank.notification.model.Notification;
import com.bank.notification.repository.NotificationRepository;
import com.bank.notification.service.NotificationDispatcher;
import com.bank.notification.service.NotificationKafkaConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationKafkaConsumer.
 * Calls consume() directly with JSON strings — no Kafka broker needed.
 */
@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDispatcher dispatcher;
    @InjectMocks private NotificationKafkaConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void injectObjectMapper() {
        try {
            var f = NotificationKafkaConsumer.class.getDeclaredField("objectMapper");
            f.setAccessible(true); f.set(consumer, objectMapper);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @BeforeEach void setUp() { injectObjectMapper(); }

    private String txEvent(long customerId, long accountId, String op, String amount, String ref) {
        return String.format("{\"customerId\":%d,\"accountId\":%d,\"operation\":\"%s\",\"amount\":\"%s\",\"referenceNo\":\"%s\",\"currency\":\"PHP\"}",
                customerId, accountId, op, amount, ref);
    }

    @Test @DisplayName("consume: CREDIT event persists SENT notification when dispatch succeeds")
    void consume_creditEvent_persistsSentNotification() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);

        consumer.consume(txEvent(1L, 101L, "CREDIT", "500.00", "REF-001"));

        verify(notificationRepository, times(1)).save(cap.capture());
        Notification saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getCustomerId()).isEqualTo(1L);
        assertThat(saved.getMessage()).contains("credited").contains("REF-001");
    }

    @Test @DisplayName("consume: DEBIT event persists FAILED notification when dispatch fails")
    void consume_debitEvent_persistsFailedNotification() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(false);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);

        consumer.consume(txEvent(2L, 102L, "DEBIT", "100.00", "REF-002"));

        verify(notificationRepository, times(1)).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test @DisplayName("consume: PHP currency shows peso symbol in alert message")
    void consume_phpCurrency_showsPesoSymbol() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);

        consumer.consume(txEvent(1L, 101L, "CREDIT", "250.00", "REF-003"));

        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getMessage()).contains("\u20B1");
    }

    @Test @DisplayName("consume: notification message contains account ID")
    void consume_messageContainsAccountId() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        consumer.consume(txEvent(1L, 999L, "DEBIT", "50.00", "REF-004"));
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getMessage()).contains("999");
    }

    @Test @DisplayName("consume: dispatcher is always called exactly once per event")
    void consume_dispatcherCalledOnce() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        consumer.consume(txEvent(1L, 101L, "CREDIT", "100.00", "REF-005"));
        verify(dispatcher, times(1)).dispatch(anyLong(), anyString(), anyString());
    }

    @Test @DisplayName("consume: malformed JSON is handled gracefully — no exception propagated")
    void consume_malformedJson_handledGracefully() {
        assertThatNoException().isThrownBy(() -> consumer.consume("NOT_JSON{{{"));
        verify(notificationRepository, never()).save(any());
    }

    @Test @DisplayName("consume: empty JSON object uses defaults without throwing")
    void consume_emptyJsonObject_usesDefaults() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        assertThatNoException().isThrownBy(() -> consumer.consume("{}"));
        verify(notificationRepository, atMostOnce()).save(any());
    }

    @Test @DisplayName("consume: account_id and reference_no are stored on the notification")
    void consume_storesAccountAndReference() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        consumer.consume(txEvent(1L, 101L, "CREDIT", "100.00", "REF-006"));
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getAccountId()).isEqualTo(101L);
        assertThat(cap.getValue().getReferenceNo()).isEqualTo("REF-006");
    }

    @Test @DisplayName("consume: a redelivered event for the same ref + account is not sent twice")
    void consume_duplicateEvent_skipped() {
        when(notificationRepository.existsByReferenceNoAndAccountId("REF-007", 101L)).thenReturn(true);
        consumer.consume(txEvent(1L, 101L, "CREDIT", "100.00", "REF-007"));
        verifyNoInteractions(dispatcher);
        verify(notificationRepository, never()).save(any());
    }

    private Notification consumeLoanEvent(String json) {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        consumer.consume(json);
        verify(notificationRepository).save(cap.capture());
        return cap.getValue();
    }

    @Test @DisplayName("loan.application.decided → 'Your loan application LAP-… has a COUNTER OFFER.'")
    void loanDecided_message() {
        Notification n = consumeLoanEvent("{\"eventType\":\"loan.application.decided\",\"customerId\":2,\"accountId\":4,"
                + "\"applicationReferenceNo\":\"LAP-20261005-000014\",\"decision\":\"COUNTER_OFFER\"}");
        assertThat(n.getMessage()).isEqualTo("Your loan application LAP-20261005-000014 has a COUNTER OFFER.");
        assertThat(n.getReferenceNo()).isEqualTo("LAP-20261005-000014");
        assertThat(n.getCustomerId()).isEqualTo(2L);
        assertThat(n.getAccountId()).isEqualTo(4L);
    }

    @Test @DisplayName("loan.disbursed → '₱250,000.00 has been credited to your account. First payment due 2026-11-05.'")
    void loanDisbursed_message() {
        Notification n = consumeLoanEvent("{\"eventType\":\"loan.disbursed\",\"customerId\":2,\"accountId\":4,"
                + "\"loanReferenceNo\":\"LN-20261005-000002\",\"amount\":250000.00,\"firstDueDate\":\"2026-11-05\"}");
        assertThat(n.getMessage()).isEqualTo("₱250,000.00 has been credited to your account. First payment due 2026-11-05.");
    }

    @Test @DisplayName("loan.repayment.posted → 'Payment of ₱9,038.10 received for loan LN-…'")
    void loanRepayment_message() {
        Notification n = consumeLoanEvent("{\"eventType\":\"loan.repayment.posted\",\"customerId\":2,\"accountId\":4,"
                + "\"loanReferenceNo\":\"LN-20261005-000002\",\"repaymentReferenceNo\":\"LRP-20261105-000003\",\"amount\":9038.10}");
        assertThat(n.getMessage()).isEqualTo("Payment of ₱9,038.10 received for loan LN-20261005-000002.");
        assertThat(n.getReferenceNo()).isEqualTo("LRP-20261105-000003");
    }

    @Test @DisplayName("loan.installment.overdue → penalty message, one alert per installment")
    void loanOverdue_message() {
        Notification n = consumeLoanEvent("{\"eventType\":\"loan.installment.overdue\",\"customerId\":2,\"accountId\":4,"
                + "\"loanReferenceNo\":\"LN-20261005-000002\",\"installmentNo\":1,\"dueDate\":\"2026-11-05\",\"penalty\":180.76}");
        assertThat(n.getMessage()).isEqualTo("Your installment due 2026-11-05 is overdue. A penalty of ₱180.76 was added.");
        assertThat(n.getReferenceNo()).isEqualTo("LN-20261005-000002-I1");
    }

    @Test @DisplayName("loan.closed → 'Loan LN-… is fully paid. Thank you!'")
    void loanClosed_message() {
        Notification n = consumeLoanEvent("{\"eventType\":\"loan.closed\",\"customerId\":2,\"accountId\":4,"
                + "\"loanReferenceNo\":\"LN-20261005-000002\"}");
        assertThat(n.getMessage()).isEqualTo("Loan LN-20261005-000002 is fully paid. Thank you!");
    }

    @Test @DisplayName("unknown loan.* events are ignored without throwing")
    void unknownLoanEvent_ignored() {
        assertThatNoException().isThrownBy(() -> consumer.consume("{\"eventType\":\"loan.something.else\",\"customerId\":2}"));
        verifyNoInteractions(dispatcher);
    }

    @Test @DisplayName("consume: referenceNo appears in the saved notification message")
    void consume_referenceNoInMessage() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        consumer.consume(txEvent(3L, 103L, "CREDIT", "750.00", "TX-PH-UNIQUE-99"));
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getMessage()).contains("TX-PH-UNIQUE-99");
    }
}
