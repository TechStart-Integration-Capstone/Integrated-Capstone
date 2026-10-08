package com.bank.loan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * OUTBOX_EVENT row for loan.* events. transaction_id stays NULL (no ledger transaction);
 * aggregate_id carries the loan/application reference and becomes the Kafka key.
 * Always saved in the same DB transaction as the state change it announces.
 */
@Entity
@Table(name = "OUTBOX_EVENT", schema = "app")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "aggregate_id", length = 40)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    public OutboxEvent() {}

    public OutboxEvent(String eventType, String aggregateId, String payload, LocalDateTime createdDate) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdDate = createdDate;
    }

    public Long getEventId() { return eventId; }
    public Long getTransactionId() { return transactionId; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
