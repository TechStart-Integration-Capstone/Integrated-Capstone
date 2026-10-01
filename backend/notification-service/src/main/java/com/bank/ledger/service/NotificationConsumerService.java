package com.bank.ledger.service;

import com.bank.ledger.model.postgres.Notification;
import com.bank.ledger.repository.postgres.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class NotificationConsumerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumerService.class);
    private final NotificationRepository notificationRepository;

    public NotificationConsumerService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Alert for ONE ledger leg (called once per entry in the event):
     * 1. Claim: insert a PENDING row. UNIQUE (reference_no, account_id) means only one delivery can win.
     * 2. Dispatch: send the alert via the gateway (only the winner reaches here).
     * 3. Persist the final status: SENT / FAILED / RETRY.
     *
     * No method-level @Transactional: each repository call commits on its own,
     * so a duplicate insert fails cleanly instead of poisoning an outer transaction.
     */
    public Notification consumeNotificationEvent(Long customerId, Long accountId, String entryType,
                                                 BigDecimal amount, String referenceNo) {
        // Fast path for normal redeliveries
        if (notificationRepository.existsByReferenceNoAndAccountId(referenceNo, accountId)) {
            log.info("Notification for ref={} account={} already claimed. Skipping duplicate.", referenceNo, accountId);
            return null;
        }

        String verb = "CREDIT".equalsIgnoreCase(entryType) ? "credited to" : "debited from";
        String message = String.format("PayPink Alert: ₱%,.4f has been %s Account ID %d. Ref: %s.",
                amount, verb, accountId, referenceNo);

        // Step 1: Claim (race-safe; the UNIQUE constraint decides the winner)
        Notification notification;
        try {
            notification = notificationRepository.saveAndFlush(
                    new Notification(customerId, accountId, referenceNo, message, "PENDING"));
        } catch (DataIntegrityViolationException dup) {
            log.info("Concurrent duplicate for ref={} account={}. Another consumer claimed it.", referenceNo, accountId);
            return null;
        }

        // Step 2: Dispatch (only the claimant sends)
        String deliveryStatus = dispatchAlertToExternalGateway(customerId, message);

        // Step 3: Persist final status
        notification.setStatus(deliveryStatus);
        notification.setUpdatedDate(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    private String dispatchAlertToExternalGateway(Long customerId, String message) {
        try {
            // Simulated digital SMS/Email gateway dispatch
            log.info("Dispatching alert to customer {}: {}", customerId, message);
            return "SENT";
        } catch (Exception ex) {
            return "RETRY";
        }
    }
}
