package com.bank.transaction.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OUTBOX_EVENT")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "event_id") private Long eventId;
    @Column(name = "transaction_id") private Long transactionId;
    @Column(name = "event_type") private String eventType;
    @Column(name = "payload", columnDefinition = "CLOB") private String payload;
    @Column(name = "status") private String status = "PENDING";
    @Column(name = "created_date") private LocalDateTime createdDate = LocalDateTime.now();
    @Column(name = "processed_date") private LocalDateTime processedDate;

    public OutboxEvent() {}
    public OutboxEvent(Long transactionId, String eventType, String payload) {
        this.transactionId = transactionId; this.eventType = eventType; this.payload = payload;
        this.status = "PENDING"; this.createdDate = LocalDateTime.now();
    }

    public Long getEventId() { return eventId; }
    public Long getTransactionId() { return transactionId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setProcessedDate(LocalDateTime processedDate) { this.processedDate = processedDate; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
