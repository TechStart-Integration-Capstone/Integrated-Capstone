package com.bank.ledger.dto;

import java.math.BigDecimal;

public class StressTestRequest {
    private Long accountId;
    private int concurrentThreads = 10;
    private BigDecimal debitAmountPerThread = new BigDecimal("50.0000");
    private BigDecimal initialBalance = new BigDecimal("60.0000");

    public StressTestRequest() {}

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public int getConcurrentThreads() { return concurrentThreads; }
    public void setConcurrentThreads(int concurrentThreads) { this.concurrentThreads = concurrentThreads; }

    public BigDecimal getDebitAmountPerThread() { return debitAmountPerThread; }
    public void setDebitAmountPerThread(BigDecimal debitAmountPerThread) { this.debitAmountPerThread = debitAmountPerThread; }

    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }
}
