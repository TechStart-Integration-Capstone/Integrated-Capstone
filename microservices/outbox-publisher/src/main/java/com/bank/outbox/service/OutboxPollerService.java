package com.bank.outbox.service;

import com.bank.outbox.model.OutboxEvent;
import com.bank.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core of the Outbox Publisher service.
 *
 * Two scheduled jobs run on independent intervals:
 *
 *   pollPendingEvents()  — runs every 5 s (configurable)
 *     Picks up PENDING outbox rows written by the transaction-service
 *     inside its ACID commit, publishes them to Kafka, marks PROCESSED.
 *     This is the primary delivery path.
 *
 *   retryFailedEvents()  — runs every 30 s (configurable)
 *     Re-attempts any rows that landed in FAILED status on a previous
 *     poll cycle. Keeps retrying until maxRetries is reached, after
 *     which the row is marked DEAD_LETTER for manual inspection.
 *
 * Both jobs are @Transactional so Oracle status updates are committed
 * atomically after each successful Kafka send.  If Kafka is unavailable
 * the status stays PENDING / FAILED and the row is retried next cycle —
 * at-least-once delivery is guaranteed without a distributed transaction.
 *
 * Kafka sends use async CompletableFuture callbacks to avoid blocking the
 * scheduler thread, but we wait for all futures in the batch to complete
 * before committing the Oracle status updates for that cycle.
 */
@Service
public class OutboxPollerService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerService.class);

    static final String TOPIC = "ledger.transaction.events";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries;

    // ── Metrics exposed via /actuator/prometheus ──────────────────────────────
    private final AtomicLong totalPublished  = new AtomicLong(0);
    private final AtomicLong totalFailed     = new AtomicLong(0);
    private final AtomicLong totalDeadLetter = new AtomicLong(0);

    public OutboxPollerService(OutboxEventRepository outboxRepository,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate    = kafkaTemplate;
    }

    // ── Primary poll — every 5 seconds ───────────────────────────────────────

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void pollPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findPendingBatch(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("[outbox-publisher] Poll cycle: {} PENDING event(s) found.", pending.size());
        publishBatch(pending);
    }

    // ── Retry sweep — every 30 seconds ───────────────────────────────────────

    @Scheduled(fixedDelayString = "${app.outbox.retry-interval-ms:30000}")
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEvent> failed = outboxRepository.findFailedBatch(batchSize);
        if (failed.isEmpty()) {
            return;
        }

        log.info("[outbox-publisher] Retry sweep: {} FAILED event(s) queued for re-attempt.", failed.size());
        publishBatch(failed);
    }

    // ── Internal: publish a batch and update status atomically ───────────────

    private void publishBatch(List<OutboxEvent> events) {
        for (OutboxEvent event : events) {
            try {
                CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                        TOPIC,
                        String.valueOf(event.getTransactionId()),
                        event.getPayload()
                );

                // Block per event so the @Transactional Oracle UPDATE stays atomic
                // with the outcome of each individual send.
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        markProcessed(event);
                        totalPublished.incrementAndGet();
                        log.info("[outbox-publisher] Published eventId={} txId={} offset={}",
                                event.getEventId(),
                                event.getTransactionId(),
                                result.getRecordMetadata().offset());
                    } else {
                        markFailed(event);
                        totalFailed.incrementAndGet();
                        log.error("[outbox-publisher] Kafka send FAILED eventId={} txId={}: {}",
                                event.getEventId(), event.getTransactionId(), ex.getMessage());
                    }
                }).join(); // wait so @Transactional commit covers the status update

            } catch (Exception ex) {
                markFailed(event);
                totalFailed.incrementAndGet();
                log.error("[outbox-publisher] Unexpected error for eventId={}: {}",
                        event.getEventId(), ex.getMessage());
            }
        }
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private void markProcessed(OutboxEvent event) {
        event.setStatus("PROCESSED");
        event.setProcessedDate(LocalDateTime.now());
        outboxRepository.save(event);
    }

    private void markFailed(OutboxEvent event) {
        // Promote to DEAD_LETTER once max retries exhausted;
        // otherwise keep as FAILED so the retry sweep picks it up again.
        if ("FAILED".equals(event.getStatus()) && reachedMaxRetries(event)) {
            event.setStatus("DEAD_LETTER");
            totalDeadLetter.incrementAndGet();
            log.warn("[outbox-publisher] eventId={} promoted to DEAD_LETTER after {} retry cycles.",
                    event.getEventId(), maxRetries);
        } else {
            event.setStatus("FAILED");
        }
        event.setProcessedDate(LocalDateTime.now());
        outboxRepository.save(event);
    }

    /**
     * Heuristic retry-count check.
     * A row is considered to have exhausted retries if its created_date is older
     * than (maxRetries × retryIntervalMs) milliseconds — no extra column needed.
     */
    private boolean reachedMaxRetries(OutboxEvent event) {
        if (event.getCreatedDate() == null) return false;
        long ageSeconds = java.time.Duration.between(
                event.getCreatedDate(), LocalDateTime.now()).getSeconds();
        // default: 5 retries × 30 s = 150 s
        long thresholdSeconds = (long) maxRetries * 30;
        return ageSeconds > thresholdSeconds;
    }

    // ── Metrics accessors (used by health/actuator) ───────────────────────────

    public long getTotalPublished()  { return totalPublished.get(); }
    public long getTotalFailed()     { return totalFailed.get(); }
    public long getTotalDeadLetter() { return totalDeadLetter.get(); }
}
