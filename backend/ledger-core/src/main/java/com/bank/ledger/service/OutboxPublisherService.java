package com.bank.ledger.service;

import com.bank.ledger.event.KafkaTopics;
import com.bank.ledger.model.oracle.OutboxEvent;
import com.bank.ledger.repository.oracle.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transactional Outbox relay (the ONLY publisher).
 *
 * Every second: claim up to 100 PENDING events (SELECT ... FOR UPDATE SKIP LOCKED),
 * publish each to Kafka and wait for the broker ack, then mark PROCESSED.
 * On failure: exponential backoff (2^n s, max 60 s); after 5 attempts -> DEAD.
 *
 * An event is only marked PROCESSED after Kafka has acknowledged it,
 * so an event can never be "processed" without actually being published.
 */
@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                  ObjectMapper objectMapper,
                                  KafkaTemplate<String, String> kafkaTemplate) {   // required: no Kafka, no app
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndPublishPendingOutboxEvents() {
        List<OutboxEvent> claimedEvents = outboxEventRepository.claimBatch(
                LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : claimedEvents) {
            try {
                publishEvent(event);
                event.setStatus("PROCESSED");
                event.setProcessedDate(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception ex) {
                markFailedAttempt(event, ex);
            }
            outboxEventRepository.save(event);
        }
    }

    private void publishEvent(OutboxEvent event) throws Exception {
        JsonNode node = objectMapper.readTree(event.getPayload());
        JsonNode accountIdNode = node.get("accountId");
        if (accountIdNode == null || accountIdNode.isNull()) {
            // A malformed payload must not be published with a made-up key; retry -> DEAD for inspection
            throw new IllegalStateException("Outbox payload for event " + event.getEventId() + " has no accountId");
        }
        String partitionKey = accountIdNode.asText();   // same account -> same partition -> ordered

        kafkaTemplate.send(KafkaTopics.TRANSACTION_EVENTS, partitionKey, event.getPayload())
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);   // wait for broker ack (acks=all)

        log.info("Published outbox event {} (account {}) to {}", event.getEventId(), partitionKey,
                KafkaTopics.TRANSACTION_EVENTS);
    }

    private void markFailedAttempt(OutboxEvent event, Exception ex) {
        int attempt = event.getRetryCount() + 1;
        event.setRetryCount(attempt);
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        event.setLastError(msg.substring(0, Math.min(msg.length(), 490)));

        if (attempt >= MAX_ATTEMPTS) {
            event.setStatus("DEAD");
            log.error("Outbox event {} marked DEAD after {} attempts: {}", event.getEventId(), attempt, msg);
        } else {
            long backoffSec = Math.min((long) Math.pow(2, attempt), 60);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSec));
            log.warn("Outbox event {} failed (attempt {}), retrying in {}s: {}", event.getEventId(), attempt, backoffSec, msg);
        }
    }
}
