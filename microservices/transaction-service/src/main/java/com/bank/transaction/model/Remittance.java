package com.bank.transaction.model;

import com.bank.transaction.dto.RemittanceRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "REMITTANCE", uniqueConstraints = @UniqueConstraint(name = "uq_remittance_customer_idemp", columnNames = {"caller_customer_id", "idempotency_key"}))
public class Remittance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "remittance_id")
    private Long remittanceId;

    @Column(name = "reference_no", nullable = false, unique = true, length = 64)
    private String referenceNo;

    @Column(name = "caller_customer_id", nullable = false)
    private Long callerCustomerId;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private Long targetAccountId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "PHP";

    // ── 7 Admin Dashboard Visible Statuses ─────────────────────────────
    public static final String STATUS_INITIATED = "Initiated";
    public static final String STATUS_AUTHORIZED = "Authorized";
    public static final String STATUS_PROCESSING = "Processing";
    public static final String STATUS_POSTED = "Posted";
    public static final String STATUS_FAILED = "Failed";
    public static final String STATUS_RESERVED = "Reserved";
    public static final String STATUS_CANCELLED = "Cancelled";

    // ── 11 Granular Internal Lifecycle Steps ───────────────────────────
    public static final String STEP_INITIATED = "INITIATED";
    public static final String STEP_VALIDATED = "VALIDATED";
    public static final String STEP_AUTHENTICATED = "AUTHENTICATED";
    public static final String STEP_FRAUD_CHECK = "FRAUD_CHECK";
    public static final String STEP_LIMIT_CHECK = "LIMIT_CHECK";
    public static final String STEP_FUNDS_CHECK = "FUNDS_CHECK";
    public static final String STEP_AUTHORIZED = "AUTHORIZED";
    public static final String STEP_POSTED = "POSTED";
    public static final String STEP_LEDGER_UPDATE = "LEDGER_UPDATE";
    public static final String STEP_NOTIFICATION = "NOTIFICATION";
    public static final String STEP_RECONCILIATION = "RECONCILIATION";

    @Column(name = "status", nullable = false, length = 30)
    private String status; // Initiated, Authorized, Processing, Posted, Failed, Reserved, Cancelled

    @Column(name = "internal_status", length = 40)
    private String internalStatus; // Step 1 to 11

    @Column(name = "current_service", length = 40)
    private String currentService;

    @Column(name = "risk_score", precision = 5, scale = 4)
    private BigDecimal riskScore;

    @Column(name = "risk_decision", length = 20)
    private String riskDecision; // APPROVE, REJECT

    @Column(name = "ft_reference", length = 64)
    private String ftReference;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType = RemittanceRequest.TYPE_TRANSFER; // TRANSFER, LOAN_DISBURSEMENT, LOAN_REPAYMENT

    public Remittance() {}

    public Remittance(String referenceNo, Long sourceAccountId, Long targetAccountId, BigDecimal amount, String currency, String status) {
        this.referenceNo = referenceNo;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.currency = currency != null ? currency : "PHP";
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getRemittanceId() { return remittanceId; }
    public void setRemittanceId(Long remittanceId) { this.remittanceId = remittanceId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public Long getCallerCustomerId() { return callerCustomerId; }
    public void setCallerCustomerId(Long callerCustomerId) { this.callerCustomerId = callerCustomerId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(Long sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public Long getTargetAccountId() { return targetAccountId; }
    public void setTargetAccountId(Long targetAccountId) { this.targetAccountId = targetAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = LocalDateTime.now(); }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public String getRiskDecision() { return riskDecision; }
    public void setRiskDecision(String riskDecision) { this.riskDecision = riskDecision; }

    public String getFtReference() { return ftReference; }
    public void setFtReference(String ftReference) { this.ftReference = ftReference; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getInternalStatus() { return internalStatus; }
    public void setInternalStatus(String internalStatus) { this.internalStatus = internalStatus; this.updatedAt = LocalDateTime.now(); }

    public String getCurrentService() { return currentService; }
    public void setCurrentService(String currentService) { this.currentService = currentService; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
