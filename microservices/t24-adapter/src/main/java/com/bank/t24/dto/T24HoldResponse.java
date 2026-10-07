package com.bank.t24.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class T24HoldResponse {

    private Long holdId;
    private Long accountId;
    private String accountNumber;
    private BigDecimal amount;
    private String currency;
    private String referenceNo;
    private String status; // ACTIVE, RELEASED, SETTLED
    private BigDecimal availableBalanceAfter;
    private LocalDateTime createdAt;
    private String message;

    public T24HoldResponse() {}

    public T24HoldResponse(Long holdId, Long accountId, String accountNumber, BigDecimal amount,
                           String currency, String referenceNo, String status,
                           BigDecimal availableBalanceAfter, LocalDateTime createdAt, String message) {
        this.holdId = holdId;
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.currency = currency;
        this.referenceNo = referenceNo;
        this.status = status;
        this.availableBalanceAfter = availableBalanceAfter;
        this.createdAt = createdAt;
        this.message = message;
    }

    public Long getHoldId() { return holdId; }
    public void setHoldId(Long holdId) { this.holdId = holdId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAvailableBalanceAfter() { return availableBalanceAfter; }
    public void setAvailableBalanceAfter(BigDecimal availableBalanceAfter) { this.availableBalanceAfter = availableBalanceAfter; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
