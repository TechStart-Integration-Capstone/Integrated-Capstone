package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class StressTestResult {

    private Long accountId;
    private BigDecimal startingBalance;
    private BigDecimal expectedFinalBalance;
    private BigDecimal actualFinalBalance;
    private int totalAttemptedRequests;
    private int successfulRequests;
    private int rejectedRequests;
    private boolean raceConditionPrevented;
    private long totalExecutionTimeMs;
    private double measuredTps;
    private List<ThreadExecutionDetail> threadLogs = new ArrayList<>();

    public StressTestResult() {}

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getStartingBalance() { return startingBalance; }
    public void setStartingBalance(BigDecimal startingBalance) { this.startingBalance = startingBalance; }

    public BigDecimal getExpectedFinalBalance() { return expectedFinalBalance; }
    public void setExpectedFinalBalance(BigDecimal expectedFinalBalance) { this.expectedFinalBalance = expectedFinalBalance; }

    public BigDecimal getActualFinalBalance() { return actualFinalBalance; }
    public void setActualFinalBalance(BigDecimal actualFinalBalance) { this.actualFinalBalance = actualFinalBalance; }

    public int getTotalAttemptedRequests() { return totalAttemptedRequests; }
    public void setTotalAttemptedRequests(int totalAttemptedRequests) { this.totalAttemptedRequests = totalAttemptedRequests; }

    public int getSuccessfulRequests() { return successfulRequests; }
    public void setSuccessfulRequests(int successfulRequests) { this.successfulRequests = successfulRequests; }

    public int getRejectedRequests() { return rejectedRequests; }
    public void setRejectedRequests(int rejectedRequests) { this.rejectedRequests = rejectedRequests; }

    public boolean isRaceConditionPrevented() { return raceConditionPrevented; }
    public void setRaceConditionPrevented(boolean raceConditionPrevented) { this.raceConditionPrevented = raceConditionPrevented; }

    public long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
    public void setTotalExecutionTimeMs(long totalExecutionTimeMs) { this.totalExecutionTimeMs = totalExecutionTimeMs; }

    public double getMeasuredTps() { return measuredTps; }
    public void setMeasuredTps(double measuredTps) { this.measuredTps = measuredTps; }

    public List<ThreadExecutionDetail> getThreadLogs() { return threadLogs; }
    public void setThreadLogs(List<ThreadExecutionDetail> threadLogs) { this.threadLogs = threadLogs; }

    // ---------------------------------------------------------------
    // Nested: per-thread execution record
    // ---------------------------------------------------------------
    public static class ThreadExecutionDetail {
        private int threadIndex;
        private String threadName;
        private String status; // COMMITTED | REJECTED_INSUFFICIENT_FUNDS | ERROR
        private String message;
        private long latencyMs;

        public ThreadExecutionDetail() {}

        public ThreadExecutionDetail(int threadIndex, String threadName,
                                     String status, String message, long latencyMs) {
            this.threadIndex = threadIndex;
            this.threadName  = threadName;
            this.status      = status;
            this.message     = message;
            this.latencyMs   = latencyMs;
        }

        public int getThreadIndex() { return threadIndex; }
        public void setThreadIndex(int threadIndex) { this.threadIndex = threadIndex; }

        public String getThreadName() { return threadName; }
        public void setThreadName(String threadName) { this.threadName = threadName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    }
}
