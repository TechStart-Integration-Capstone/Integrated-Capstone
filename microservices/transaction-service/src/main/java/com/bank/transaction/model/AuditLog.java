package com.bank.transaction.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "AUDIT_LOG")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "audit_id") private Long auditId;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "action") private String action;
    @Column(name = "entity") private String entity;
    @Column(name = "details", length = 4000) private String details;
    @Column(name = "timestamp") private LocalDateTime timestamp = LocalDateTime.now();

    public AuditLog() {}
    public AuditLog(Long customerId, String action, String entity, String details) {
        this.customerId = customerId; this.action = action; this.entity = entity;
        this.details = details; this.timestamp = LocalDateTime.now();
    }
}
