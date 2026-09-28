package com.bank.reconciliation.model.oracle;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTION")
public class TransactionRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id") private Long transactionId;
    @Column(name = "from_account_id") private Long fromAccountId;
    @Column(name = "to_account_id") private Long toAccountId;
    @Column(name = "amount", precision = 18, scale = 4) private BigDecimal amount;
    @Column(name = "transaction_type") private String transactionType;
    @Column(name = "reference_no") private String referenceNo;
    @Column(name = "status") private String status;
    @Column(name = "transaction_date") private LocalDateTime transactionDate;

    public Long getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
    public String getReferenceNo() { return referenceNo; }
    public String getStatus() { return status; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
}
