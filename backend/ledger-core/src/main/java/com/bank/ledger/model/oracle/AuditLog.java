package com.bank.ledger.model.oracle;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "AUDIT_LOG")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "action", nullable = false, length = 100)
    private String action; // e.g. 'USER_LOGIN', 'TRANSFER_INITIATED', 'MUTATION_EXECUTED'

    @Column(name = "entity", nullable = false, length = 50)
    private String entity; // e.g. 'ACCOUNT', 'CUSTOMER', 'AUTH'

    @Column(name = "details", nullable = false, length = 4000)
    private String details;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public AuditLog() {}

    public AuditLog(Long customerId, String action, String entity, String details) {
        this.customerId = customerId;
        this.action = action;
        this.entity = entity;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
