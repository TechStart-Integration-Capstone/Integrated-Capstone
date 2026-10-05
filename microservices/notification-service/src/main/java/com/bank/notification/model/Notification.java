package com.bank.notification.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One alert per ledger leg / loan event: UNIQUE (reference_no, account_id). */
@Entity
@Table(name = "NOTIFICATION")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id") private Long notificationId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Column(name = "account_id", nullable = false) private Long accountId;
    @Column(name = "reference_no", nullable = false, length = 64) private String referenceNo;
    @Column(name = "message", nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "status", nullable = false, length = 20) private String status;
    @Column(name = "created_date", nullable = false, updatable = false) private LocalDateTime createdDate = LocalDateTime.now();
    @Column(name = "updated_date") private LocalDateTime updatedDate;

    public Notification() {}
    public Notification(Long customerId, Long accountId, String referenceNo, String message, String status) {
        this.customerId = customerId;
        this.accountId = accountId;
        this.referenceNo = referenceNo;
        this.message = message;
        this.status = status;
        this.createdDate = LocalDateTime.now();
    }

    public Long getNotificationId() { return notificationId; }
    public Long getCustomerId()     { return customerId; }
    public Long getAccountId()      { return accountId; }
    public String getReferenceNo()  { return referenceNo; }
    public String getMessage()      { return message; }
    public String getStatus()       { return status; }
    public void setStatus(String status) { this.status = status; this.updatedDate = LocalDateTime.now(); }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public LocalDateTime getUpdatedDate() { return updatedDate; }
}
