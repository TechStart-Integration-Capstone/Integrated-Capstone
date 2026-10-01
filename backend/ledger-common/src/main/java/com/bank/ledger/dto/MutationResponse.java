package com.bank.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MutationResponse {

    private Long transactionId;
    private String referenceNo;
    private Long accountId;
    private String accountNumber;
    private String operation;
    private BigDecimal amount;
    private String currency;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private String status;
    private boolean isCachedIdempotentResponse;
    private LocalDateTime timestamp;

    public MutationResponse() {}

    public MutationResponse(Long transactionId, String referenceNo, Long accountId, String accountNumber,
                            String operation, BigDecimal amount, String currency, BigDecimal beforeBalance,
                            BigDecimal afterBalance, String status, boolean isCachedIdempotentResponse) {
        this.transactionId = transactionId;
        this.referenceNo = referenceNo;
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.operation = operation;
        this.amount = amount;
        this.currency = currency;
        this.beforeBalance = beforeBalance;
        this.afterBalance = afterBalance;
        this.status = status;
        this.isCachedIdempotentResponse = isCachedIdempotentResponse;
        this.timestamp = LocalDateTime.now();
    }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getBeforeBalance() { return beforeBalance; }
    public void setBeforeBalance(BigDecimal beforeBalance) { this.beforeBalance = beforeBalance; }

    public BigDecimal getAfterBalance() { return afterBalance; }
    public void setAfterBalance(BigDecimal afterBalance) { this.afterBalance = afterBalance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isCachedIdempotentResponse() { return isCachedIdempotentResponse; }
    public void setCachedIdempotentResponse(boolean cachedIdempotentResponse) { isCachedIdempotentResponse = cachedIdempotentResponse; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
