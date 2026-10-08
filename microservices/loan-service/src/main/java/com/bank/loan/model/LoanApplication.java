package com.bank.loan.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "LOAN_APPLICATION", schema = "app")
public class LoanApplication {

    public static final String STATUS_DECIDED = "DECIDED";
    public static final String STATUS_DISBURSING = "DISBURSING"; // accepted; disbursement started, loan not recorded yet
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_FAILED = "FAILED";         // disbursement definitively rejected; nothing credited
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "reference_no", nullable = false, unique = true, length = 30)
    private String referenceNo;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "requested_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "requested_term", nullable = false)
    private Integer requestedTerm;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore;

    @Column(name = "decision", nullable = false, length = 15)
    private String decision; // APPROVED | COUNTER_OFFER | DECLINED

    @Column(name = "offered_amount", precision = 18, scale = 4)
    private BigDecimal offeredAmount;

    @Column(name = "offered_term")
    private Integer offeredTerm;

    @Column(name = "annual_rate", precision = 6, scale = 3)
    private BigDecimal annualRate;

    @Column(name = "monthly_installment", precision = 18, scale = 4)
    private BigDecimal monthlyInstallment;

    @Column(name = "decline_reason", length = 50)
    private String declineReason;

    @Column(name = "status", nullable = false, length = 15)
    private String status; // DECIDED | DISBURSING | ACCEPTED | FAILED | EXPIRED

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // UTC

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate; // UTC

    public LoanApplication() {}

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public Integer getRequestedTerm() { return requestedTerm; }
    public void setRequestedTerm(Integer requestedTerm) { this.requestedTerm = requestedTerm; }
    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public BigDecimal getOfferedAmount() { return offeredAmount; }
    public void setOfferedAmount(BigDecimal offeredAmount) { this.offeredAmount = offeredAmount; }
    public Integer getOfferedTerm() { return offeredTerm; }
    public void setOfferedTerm(Integer offeredTerm) { this.offeredTerm = offeredTerm; }
    public BigDecimal getAnnualRate() { return annualRate; }
    public void setAnnualRate(BigDecimal annualRate) { this.annualRate = annualRate; }
    public BigDecimal getMonthlyInstallment() { return monthlyInstallment; }
    public void setMonthlyInstallment(BigDecimal monthlyInstallment) { this.monthlyInstallment = monthlyInstallment; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
}
