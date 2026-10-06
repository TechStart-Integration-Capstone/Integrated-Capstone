package com.bank.transaction.dto;

import java.math.BigDecimal;

public class RemittanceResponse {

    private String status;               // "POSTED", "REJECTED", "PROCESSING"
    private String referenceNo;
    private String ftReference;
    private Object sourceAccountId;
    private Object targetAccountId;
    private BigDecimal amount;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private BigDecimal riskScore;
    private String riskDecision;
    private String reason;
    private boolean cachedIdempotentResponse;
    private Long transactionId;          // LEDGER_TRANSACTION id once POSTED
    private java.time.LocalDateTime cancelUntil;
    private Integer cancelWindowSeconds;
    private Boolean canCancel;

    public RemittanceResponse() {}

    public RemittanceResponse(String status, String referenceNo, String ftReference, Object sourceAccountId,
                              Object targetAccountId, BigDecimal amount, BigDecimal beforeBalance, BigDecimal afterBalance,
                              BigDecimal riskScore, String riskDecision, String reason, boolean cachedIdempotentResponse) {
        this.status = status;
        this.referenceNo = referenceNo;
        this.ftReference = ftReference;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.beforeBalance = beforeBalance;
        this.afterBalance = afterBalance;
        this.riskScore = riskScore;
        this.riskDecision = riskDecision;
        this.reason = reason;
        this.cachedIdempotentResponse = cachedIdempotentResponse;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getFtReference() { return ftReference; }
    public void setFtReference(String ftReference) { this.ftReference = ftReference; }

    public Object getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(Object sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public Object getTargetAccountId() { return targetAccountId; }
    public void setTargetAccountId(Object targetAccountId) { this.targetAccountId = targetAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBeforeBalance() { return beforeBalance; }
    public void setBeforeBalance(BigDecimal beforeBalance) { this.beforeBalance = beforeBalance; }

    public BigDecimal getAfterBalance() { return afterBalance; }
    public void setAfterBalance(BigDecimal afterBalance) { this.afterBalance = afterBalance; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public String getRiskDecision() { return riskDecision; }
    public void setRiskDecision(String riskDecision) { this.riskDecision = riskDecision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isCachedIdempotentResponse() { return cachedIdempotentResponse; }
    public void setCachedIdempotentResponse(boolean cachedIdempotentResponse) { this.cachedIdempotentResponse = cachedIdempotentResponse; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public java.time.LocalDateTime getCancelUntil() { return cancelUntil; }
    public void setCancelUntil(java.time.LocalDateTime cancelUntil) { this.cancelUntil = cancelUntil; }

    public Integer getCancelWindowSeconds() { return cancelWindowSeconds; }
    public void setCancelWindowSeconds(Integer cancelWindowSeconds) { this.cancelWindowSeconds = cancelWindowSeconds; }

    public Boolean getCanCancel() { return canCancel; }
    public boolean isCanCancel() { return Boolean.TRUE.equals(canCancel); }
    public void setCanCancel(Boolean canCancel) { this.canCancel = canCancel; }
}
