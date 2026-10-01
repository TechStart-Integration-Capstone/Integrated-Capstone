package com.bank.ledger.model.postgres;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One customer alert per ledger leg.
 * UNIQUE (reference_no, account_id): an internal transfer produces two alerts
 * (sender DEBIT + receiver CREDIT), and a redelivered event can never create a duplicate.
 */
@Entity
@Table(name = "NOTIFICATION",
       uniqueConstraints = @UniqueConstraint(name = "uq_notification_ref_account",
                                             columnNames = {"reference_no", "account_id"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "reference_no", nullable = false, length = 64)
    private String referenceNo;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // 'PENDING', 'SENT', 'FAILED', 'RETRY'

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

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
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }
}
