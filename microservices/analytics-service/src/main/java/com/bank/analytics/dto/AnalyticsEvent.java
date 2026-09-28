package com.bank.analytics.dto;

import java.math.BigDecimal;

/**
 * Internal DTO parsed from the Kafka event payload produced by
 * LedgerMutationService. Fields mirror the Map.of() payload shape:
 *
 *   { transactionId, referenceNo, accountId, customerId,
 *     operation, amount, currency, beforeBalance, afterBalance, timestamp }
 */
public class AnalyticsEvent {

    private Long   transactionId;
    private String referenceNo;
    private Long   accountId;
    private Long   customerId;
    private String operation;       // DEBIT | CREDIT
    private BigDecimal amount;
    private String currency;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private String timestamp;

    public AnalyticsEvent() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getTransactionId()          { return transactionId; }
    public void setTransactionId(Long v)    { this.transactionId = v; }

    public String getReferenceNo()          { return referenceNo; }
    public void setReferenceNo(String v)    { this.referenceNo = v; }

    public Long getAccountId()              { return accountId; }
    public void setAccountId(Long v)        { this.accountId = v; }

    public Long getCustomerId()             { return customerId; }
    public void setCustomerId(Long v)       { this.customerId = v; }

    public String getOperation()            { return operation; }
    public void setOperation(String v)      { this.operation = v; }

    public BigDecimal getAmount()           { return amount; }
    public void setAmount(BigDecimal v)     { this.amount = v; }

    public String getCurrency()             { return currency; }
    public void setCurrency(String v)       { this.currency = v; }

    public BigDecimal getBeforeBalance()    { return beforeBalance; }
    public void setBeforeBalance(BigDecimal v) { this.beforeBalance = v; }

    public BigDecimal getAfterBalance()     { return afterBalance; }
    public void setAfterBalance(BigDecimal v) { this.afterBalance = v; }

    public String getTimestamp()            { return timestamp; }
    public void setTimestamp(String v)      { this.timestamp = v; }
}
