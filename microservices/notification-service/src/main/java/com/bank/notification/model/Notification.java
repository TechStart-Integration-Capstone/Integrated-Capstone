package com.bank.notification.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATION")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id") private Long notificationId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Column(name = "message", nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "status", nullable = false, length = 20) private String status;
    @Column(name = "created_date", nullable = false, updatable = false) private LocalDateTime createdDate = LocalDateTime.now();

    public Notification() {}
    public Notification(Long customerId, String message, String status) {
        this.customerId = customerId;
        this.message = message;
        this.status = status;
        this.createdDate = LocalDateTime.now();
    }

    public Long getNotificationId() { return notificationId; }
    public Long getCustomerId()     { return customerId; }
    public String getMessage()      { return message; }
    public String getStatus()       { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
