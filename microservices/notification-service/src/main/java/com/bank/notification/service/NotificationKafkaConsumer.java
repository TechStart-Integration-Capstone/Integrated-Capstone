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

    @KafkaListener(topics = {"remittance.events", "ledger.transaction.events", "savings.events"}, groupId = "notification-service-group")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            if (node.path("eventType").asText().startsWith("savings.")) {
                String type = node.path("eventType").asText();
                if (!java.util.Set.of("savings.allocated", "savings.released", "savings.circle_completed").contains(type)) return;
                if (!node.hasNonNull("customerId") || !node.hasNonNull("accountId") || !node.hasNonNull("referenceNo")) return;
                String savingsMessage = "savings.circle_completed".equals(type)
                        ? "Your PinkCircle reached its shared goal. Each member keeps their own savings; payments require separate authorization."
                        : "PHP " + node.path("amount").asText() + ("savings.released".equals(type)
                            ? " was released from savings and is available to spend." : " was reserved for your savings goals. Your money stays in your account.");
                deliver(node.get("customerId").asLong(), node.get("accountId").asLong(), node.get("referenceNo").asText(), savingsMessage);
                return;
            }
            if (LoanNotificationMessages.isLoanEvent(node)) {
                LoanNotificationMessages.build(node).ifPresent(alert ->
                        deliver(alert.customerId(), alert.accountId(), alert.referenceNo(), alert.message()));
                return;
            }

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

            deliver(customerId, accountId, refNo, alertMsg);

        } catch (Exception ex) {
            log.error("[notification-service] Failed to process notification event: {}",
                    ex.getMessage());
        }
    }

    /** Dispatch once per (reference_no, account_id) and persist the delivery record. */
    private void deliver(Long customerId, Long accountId, String refNo, String alertMsg) {
        if (notificationRepository.existsByReferenceNoAndAccountId(refNo, accountId)) {
            log.info("[notification-service] Already notified ref={} accountId={} — skipped duplicate event", refNo, accountId);
            return;
        }

        // Dispatch over all three channels (Email / SMS / Push)
        boolean delivered = dispatcher.dispatch(customerId, alertMsg, refNo);

        // Persist delivery record — status reflects actual dispatch outcome
        String status = delivered ? "SENT" : "FAILED";
        notificationRepository.save(new Notification(customerId, accountId, refNo, alertMsg, status));

        log.info("[notification-service] Notification {} for customerId={} ref={}",
                status, customerId, refNo);
    }

    private String currencySymbol(String currency) {
        return "PHP".equalsIgnoreCase(currency) ? "\u20B1" : currency + " ";
    }
}
