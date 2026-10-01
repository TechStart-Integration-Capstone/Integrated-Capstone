package com.bank.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TransactionOutboxPayload {

    private Long transactionId;
    private String referenceNo;
    private Long customerId;
    private Long accountId;
    private String operation; // 'DEBIT' or 'CREDIT'
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private String status;
    private LocalDateTime timestamp;
    private List<EntryPayload> entries;

    public TransactionOutboxPayload() {}

    public TransactionOutboxPayload(Long transactionId, String referenceNo, Long customerId, Long accountId,
                                    String operation, String transactionType, BigDecimal amount, String currency,
                                    BigDecimal beforeBalance, BigDecimal afterBalance, String status,
                                    List<EntryPayload> entries) {
        this.transactionId = transactionId;
        this.referenceNo = referenceNo;
        this.customerId = customerId;
        this.accountId = accountId;
        this.operation = operation;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.beforeBalance = beforeBalance;
        this.afterBalance = afterBalance;
        this.status = status;
        this.timestamp = LocalDateTime.now();
        this.entries = entries;
    }

    /** One ledger leg. customerId identifies the account owner (used for per-leg alerts). */
    public static class EntryPayload {
        private Long accountId;
        private Long customerId;
        private String entryType; // 'DEBIT' or 'CREDIT'
        private BigDecimal amount;
        private String currency;
        private BigDecimal beforeBalance;
        private BigDecimal afterBalance;

        public EntryPayload() {}

        public EntryPayload(Long accountId, Long customerId, String entryType, BigDecimal amount, String currency,
                            BigDecimal beforeBalance, BigDecimal afterBalance) {
            this.accountId = accountId;
            this.customerId = customerId;
            this.entryType = entryType;
            this.amount = amount;
            this.currency = currency;
            this.beforeBalance = beforeBalance;
            this.afterBalance = afterBalance;
        }

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }

        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }

        public String getEntryType() { return entryType; }
        public void setEntryType(String entryType) { this.entryType = entryType; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public BigDecimal getBeforeBalance() { return beforeBalance; }
        public void setBeforeBalance(BigDecimal beforeBalance) { this.beforeBalance = beforeBalance; }

        public BigDecimal getAfterBalance() { return afterBalance; }
        public void setAfterBalance(BigDecimal afterBalance) { this.afterBalance = afterBalance; }
    }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

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

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public List<EntryPayload> getEntries() { return entries; }
    public void setEntries(List<EntryPayload> entries) { this.entries = entries; }
}
