package com.bank.notification.service;

import com.bank.notification.model.Notification;
import com.bank.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for the notification-service.
 *
 * On each ledger.transaction.events message:
 *   1. Parse the event payload
 *   2. Build a formatted Philippine banking alert message
 *   3. Dispatch over Email + SMS + Push via NotificationDispatcher
 *   4. Persist a Notification record with status SENT or FAILED
 */
@Service
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);

    private final NotificationRepository  notificationRepository;
    private final NotificationDispatcher  dispatcher;
    private final ObjectMapper            objectMapper;

    public NotificationKafkaConsumer(NotificationRepository notificationRepository,
                                     NotificationDispatcher dispatcher,
                                     ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.dispatcher             = dispatcher;
        this.objectMapper           = objectMapper;
    }

    @KafkaListener(topics = "ledger.transaction.events", groupId = "notification-service-group")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            Long   customerId = node.has("customerId")  ? node.get("customerId").asLong()  : 1L;
            Long   accountId  = node.has("accountId")   ? node.get("accountId").asLong()   : 0L;
            String operation  = node.has("operation")   ? node.get("operation").asText()   : "DEBIT";
            String amount     = node.has("amount")      ? node.get("amount").asText()      : "0";
            String refNo      = node.has("referenceNo") ? node.get("referenceNo").asText() : "N/A";
            String currency   = node.has("currency")    ? node.get("currency").asText()    : "PHP";

            // Build alert message — matches the format shown in the architecture diagram
            String alertMsg = String.format(
                    "PayPink Alert: %s%s has been %sed on Account ID %d. " +
                    "Ref: %s. (Philippine Banking Network)",
                    currencySymbol(currency), amount,
                    operation.toLowerCase(), accountId, refNo);

            // Dispatch over all three channels (Email / SMS / Push)
            boolean delivered = dispatcher.dispatch(customerId, alertMsg, refNo);

            // Persist delivery record — status reflects actual dispatch outcome
            String status = delivered ? "SENT" : "FAILED";
            notificationRepository.save(new Notification(customerId, alertMsg, status));

            log.info("[notification-service] Notification {} for customerId={} ref={}",
                    status, customerId, refNo);

        } catch (Exception ex) {
            log.error("[notification-service] Failed to process notification event: {}",
                    ex.getMessage());
        }
    }

    private String currencySymbol(String currency) {
        return "PHP".equalsIgnoreCase(currency) ? "\u20B1" : currency + " ";
    }
}
