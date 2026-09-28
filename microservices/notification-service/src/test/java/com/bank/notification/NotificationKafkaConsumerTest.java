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

    @Test @DisplayName("consume: referenceNo appears in the saved notification message")
    void consume_referenceNoInMessage() {
        when(dispatcher.dispatch(anyLong(), anyString(), anyString())).thenReturn(true);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        consumer.consume(txEvent(3L, 103L, "CREDIT", "750.00", "TX-PH-UNIQUE-99"));
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getMessage()).contains("TX-PH-UNIQUE-99");
    }
}
