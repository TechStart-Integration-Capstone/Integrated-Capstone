package com.bank.transaction.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNT")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "account_id") private Long accountId;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "account_number") private String accountNumber;
    @Column(name = "account_type") private String accountType;
    @Column(name = "currency") private String currency;
    @Column(name = "current_balance", precision = 18, scale = 4) private BigDecimal currentBalance;
    @Column(name = "status") private String status;
    @Column(name = "created_date") private LocalDateTime createdDate;

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
