package com.bank.outbox.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA mapping of the OUTBOX_EVENT table in Oracle XE.
 *
 * This entity is READ-WRITE for the outbox-publisher:
 *   - SELECT WHERE status = 'PENDING' (polled by OutboxPollerService)
 *   - UPDATE status → 'PROCESSED' | 'FAILED'
 *   - UPDATE processed_date, retry_count
 *
 * The OUTBOX_EVENT row is originally INSERTED by the transaction-service
 * inside the same Oracle ACID transaction that mutates the account balance,
 * guaranteeing at-least-once delivery without a two-phase commit.
 */
@Entity
@Table(name = "OUTBOX_EVENT")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getEventId()         { return eventId; }
    public Long getTransactionId()   { return transactionId; }
    public String getEventType()     { return eventType; }
    public String getPayload()       { return payload; }
    public String getStatus()        { return status; }
    public LocalDateTime getCreatedDate()   { return createdDate; }
    public LocalDateTime getProcessedDate() { return processedDate; }

    // ── Setters (only mutable fields) ────────────────────────────────────────

    public void setStatus(String status)                       { this.status = status; }
    public void setProcessedDate(LocalDateTime processedDate)  { this.processedDate = processedDate; }
}
