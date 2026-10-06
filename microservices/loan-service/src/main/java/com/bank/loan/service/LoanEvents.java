package com.bank.loan.service;

import com.bank.loan.model.OutboxEvent;
import com.bank.loan.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes loan.* OUTBOX_EVENT rows and builds reference numbers.
 * Callers must already be inside the DB transaction that makes the state change.
 */
@Component
public class LoanEvents {

    public static final String APPLICATION_DECIDED = "loan.application.decided";
    public static final String DISBURSED = "loan.disbursed";
    public static final String REPAYMENT_POSTED = "loan.repayment.posted";
    public static final String INSTALLMENT_OVERDUE = "loan.installment.overdue";
    public static final String AUTODEBIT_FAILED = "loan.autodebit.failed";
    public static final String CLOSED = "loan.closed";

    public static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LoanEvents(OutboxEventRepository outboxRepository, ObjectMapper objectMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publish(String eventType, String aggregateId, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.putAll(fields);
        try {
            outboxRepository.save(new OutboxEvent(eventType, aggregateId, objectMapper.writeValueAsString(payload), nowUtc()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + eventType + " payload", e);
        }
    }

    public LocalDateTime nowUtc() {
        return LocalDateTime.now(clock.withZone(java.time.ZoneOffset.UTC));
    }

    public LocalDate todayManila() {
        return LocalDate.now(clock.withZone(MANILA));
    }

    /** e.g. LAP-20261005-000014 — the sequence is the row's identity value. */
    public String reference(String prefix, long id) {
        return prefix + "-" + todayManila().format(REF_DATE) + "-" + String.format("%06d", id);
    }

    /** Unique placeholder for the NOT NULL reference_no until the identity value is known (30 chars). */
    public static String placeholderReference() {
        return "TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
