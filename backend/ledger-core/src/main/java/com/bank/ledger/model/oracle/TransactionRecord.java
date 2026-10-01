package com.bank.ledger.model.oracle;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTION")
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id")
    private Long toAccountId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "source_currency", nullable = false, length = 10)
    private String sourceCurrency = "PHP";

    @Column(name = "target_currency", nullable = false, length = 10)
    private String targetCurrency = "PHP";

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType; // 'DEBIT', 'CREDIT', 'TRANSFER_INSTAPAY', 'TRANSFER_PESONET', 'TRANSFER_QRPH'

    @Column(name = "reference_no", nullable = false, unique = true, length = 64)
    private String referenceNo;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // 'SUCCESS', 'FAILED'

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "transaction_date", nullable = false, updatable = false)
    private LocalDateTime transactionDate = LocalDateTime.now();

    @Column(name = "operation", nullable = false, length = 10)
    private String operation; // 'DEBIT' or 'CREDIT'

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public TransactionRecord() {}

    public TransactionRecord(Long fromAccountId, Long toAccountId, BigDecimal amount, String sourceCurrency,
                             String targetCurrency, String transactionType, String referenceNo, String status, String failureReason) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.transactionType = transactionType;
        this.referenceNo = referenceNo;
        this.status = status;
        this.failureReason = failureReason;
        this.transactionDate = LocalDateTime.now();
    }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }

    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(String sourceCurrency) { this.sourceCurrency = sourceCurrency; }

    public String getTargetCurrency() { return targetCurrency; }
    public void setTargetCurrency(String targetCurrency) { this.targetCurrency = targetCurrency; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
}
