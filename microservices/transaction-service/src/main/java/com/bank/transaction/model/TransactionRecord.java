package com.bank.transaction.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTION")
public class TransactionRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "transaction_id") private Long transactionId;
    @Column(name = "from_account_id") private Long fromAccountId;
    @Column(name = "to_account_id") private Long toAccountId;
    @Column(name = "amount", precision = 18, scale = 4) private BigDecimal amount;
    @Column(name = "source_currency") private String sourceCurrency = "PHP";
    @Column(name = "target_currency") private String targetCurrency = "PHP";
    @Column(name = "transaction_type") private String transactionType;
    @Column(name = "reference_no", unique = true) private String referenceNo;
    @Column(name = "status") private String status;
    @Column(name = "failure_reason") private String failureReason;
    @Column(name = "transaction_date") private LocalDateTime transactionDate = LocalDateTime.now();

    public TransactionRecord() {}
    public TransactionRecord(Long fromAccountId, Long toAccountId, BigDecimal amount, String sourceCurrency,
                             String targetCurrency, String transactionType, String referenceNo, String status, String failureReason) {
        this.fromAccountId = fromAccountId; this.toAccountId = toAccountId; this.amount = amount;
        this.sourceCurrency = sourceCurrency; this.targetCurrency = targetCurrency; this.transactionType = transactionType;
        this.referenceNo = referenceNo; this.status = status; this.failureReason = failureReason;
        this.transactionDate = LocalDateTime.now();
    }

    public Long getTransactionId() { return transactionId; }
    public Long getFromAccountId() { return fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getSourceCurrency() { return sourceCurrency; }
    public String getReferenceNo() { return referenceNo; }
    public String getStatus() { return status; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
}
