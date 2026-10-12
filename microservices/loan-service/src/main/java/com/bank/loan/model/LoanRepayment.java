package com.bank.loan.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "LOAN_REPAYMENT", schema = "t24")
public class LoanRepayment {
    public static final String PENDING = "PENDING";
    public static final String POSTED = "POSTED";
    public static final String REJECTED = "REJECTED";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    @Column(name = "status", nullable = false, length = 24)
    private String status = POSTED; // Existing rows were saved only after successful settlement.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repayment_id")
    private Long repaymentId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "reference_no", nullable = false, unique = true, length = 30)
    private String referenceNo;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate; // UTC

    public LoanRepayment() {}
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getRepaymentId() { return repaymentId; }
    public void setRepaymentId(Long repaymentId) { this.repaymentId = repaymentId; }
    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
}
