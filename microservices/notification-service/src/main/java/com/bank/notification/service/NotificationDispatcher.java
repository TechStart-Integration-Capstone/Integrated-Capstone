package com.bank.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Mock multi-channel notification dispatcher.
 *
 * Simulates delivery over three channels — Email, SMS, and Push —
 * without requiring real SMTP, Twilio, or Firebase credentials.
 *
 * Each dispatch call produces structured log output that mirrors what
 * a real dispatcher would emit, making it demo-visible in Grafana/Loki:
 *
 *   [EMAIL]  msgId=MSG-xxxx  to=customer@paypink.ph  status=DELIVERED  at=<timestamp>
 *   [SMS]    msgId=MSG-xxxx  to=+63 917 xxx xxxx     status=DELIVERED  at=<timestamp>
 *   [PUSH]   msgId=MSG-xxxx  deviceToken=tok-xxxx    status=DELIVERED  at=<timestamp>
 *
 * Returns true if at least one channel delivered successfully so the
 * caller can set the Notification record status to SENT or FAILED.
 *
 * Architecture note: In a production system replace the log statements
 * in each channel method with the real client calls
 * (JavaMail / Twilio SDK / Firebase Admin SDK). The dispatcher interface
 * stays identical — only the internals change.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Dispatch a notification over all three channels.
     *
     * @param customerId  the recipient customer ID (used to derive mock contact info)
     * @param message     the alert text already formatted by the Kafka consumer
     * @param referenceNo the transaction reference — included in each dispatch log
     * @return true if ALL channels reported DELIVERED; false if any channel failed
     */
    public boolean dispatch(Long customerId, String message, String referenceNo) {
        String msgId      = "MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String deliveredAt = LocalDateTime.now().format(TS);

        boolean emailOk = dispatchEmail(customerId, message, msgId, referenceNo, deliveredAt);
        boolean smsOk   = dispatchSms(customerId, message, msgId, referenceNo, deliveredAt);
        boolean pushOk  = dispatchPush(customerId, message, msgId, referenceNo, deliveredAt);

        boolean allDelivered = emailOk && smsOk && pushOk;

        if (allDelivered) {
            log.info("[notification-service] All channels DELIVERED  msgId={}  customerId={}  ref={}",
                    msgId, customerId, referenceNo);
        } else {
            log.warn("[notification-service] Partial delivery  msgId={}  email={}  sms={}  push={}  ref={}",
                    msgId, emailOk ? "OK" : "FAIL", smsOk ? "OK" : "FAIL",
                    pushOk ? "OK" : "FAIL", referenceNo);
        }

        return allDelivered;
    }

    // ── Email channel ─────────────────────────────────────────────────────────

    private boolean dispatchEmail(Long customerId, String message,
                                   String msgId, String refNo, String deliveredAt) {
        try {
            // Derive a deterministic mock email from customerId
            String recipient = "customer" + customerId + "@paypink.ph";

            log.info("[EMAIL]  msgId={}  to={}  subject=\"PayPink Transaction Alert\"  " +
                     "body=\"{}\"  ref={}  status=DELIVERED  at={}",
                    msgId, recipient, truncate(message, 80), refNo, deliveredAt);

            return true;
        } catch (Exception ex) {
            log.error("[EMAIL]  msgId={}  status=FAILED  reason={}", msgId, ex.getMessage());
            return false;
        }
    }

    // ── SMS channel ───────────────────────────────────────────────────────────

    private boolean dispatchSms(Long customerId, String message,
                                 String msgId, String refNo, String deliveredAt) {
        try {
            // Derive a deterministic mock Philippine mobile number from customerId
            String recipient = "+63 917 " + String.format("%03d", customerId % 1000) + " "
                    + String.format("%04d", (customerId * 7919L) % 10000);

            // SMS body is truncated to 160 chars (single SMS segment)
            String smsBody = truncate(message, 160);

            log.info("[SMS]    msgId={}  to={}  body=\"{}\"  ref={}  status=DELIVERED  at={}",
                    msgId, recipient, smsBody, refNo, deliveredAt);

            return true;
        } catch (Exception ex) {
            log.error("[SMS]    msgId={}  status=FAILED  reason={}", msgId, ex.getMessage());
            return false;
        }
    }

    // ── Push (FCM) channel ────────────────────────────────────────────────────

    private boolean dispatchPush(Long customerId, String message,
                                  String msgId, String refNo, String deliveredAt) {
        try {
            // Derive a deterministic mock FCM device token from customerId
            String deviceToken = "tok-" + UUID.nameUUIDFromBytes(
                    ("customer:" + customerId).getBytes()).toString().substring(0, 16);

            log.info("[PUSH]   msgId={}  deviceToken={}  title=\"PayPink Alert\"  " +
                     "body=\"{}\"  ref={}  status=DELIVERED  at={}",
                    msgId, deviceToken, truncate(message, 100), refNo, deliveredAt);

            return true;
        } catch (Exception ex) {
            log.error("[PUSH]   msgId={}  status=FAILED  reason={}", msgId, ex.getMessage());
            return false;
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
