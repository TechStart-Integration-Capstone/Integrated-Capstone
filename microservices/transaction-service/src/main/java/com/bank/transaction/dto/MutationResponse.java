package com.bank.transaction.dto;

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
        this.transactionId = transactionId; this.referenceNo = referenceNo; this.accountId = accountId;
        this.accountNumber = accountNumber; this.operation = operation; this.amount = amount;
        this.currency = currency; this.beforeBalance = beforeBalance; this.afterBalance = afterBalance;
        this.status = status; this.isCachedIdempotentResponse = isCachedIdempotentResponse;
        this.timestamp = LocalDateTime.now();
    }

    public Long getTransactionId() { return transactionId; }
    public String getReferenceNo() { return referenceNo; }
    public Long getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getOperation() { return operation; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public BigDecimal getBeforeBalance() { return beforeBalance; }
    public BigDecimal getAfterBalance() { return afterBalance; }
    public String getStatus() { return status; }
    public boolean isCachedIdempotentResponse() { return isCachedIdempotentResponse; }
    public void setCachedIdempotentResponse(boolean v) { this.isCachedIdempotentResponse = v; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
