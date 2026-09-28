package com.bank.account.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNT")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "account_id") private Long accountId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Column(name = "account_number", nullable = false, unique = true, length = 30) private String accountNumber;
    @Column(name = "account_type", nullable = false, length = 30) private String accountType;
    @Column(name = "currency", nullable = false, length = 10) private String currency = "PHP";
    @Column(name = "current_balance", nullable = false, precision = 18, scale = 4) private BigDecimal currentBalance;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "created_date", nullable = false, updatable = false) private LocalDateTime createdDate = LocalDateTime.now();

    public Account() {}
    public Long getAccountId() { return accountId; }
    public Long getCustomerId() { return customerId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
