package com.bank.reconciliation.model.postgres;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "LEDGER_MUTATION_AUDIT")
public class LedgerMutationAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id") private Long auditId;
    @Column(name = "transaction_id") private Long transactionId;
    @Column(name = "account_id") private Long accountId;
    @Column(name = "operation") private String operation;
    @Column(name = "amount", precision = 18, scale = 4) private BigDecimal amount;
    @Column(name = "currency") private String currency;
    @Column(name = "before_balance", precision = 18, scale = 4) private BigDecimal beforeBalance;
    @Column(name = "after_balance", precision = 18, scale = 4) private BigDecimal afterBalance;
    @Column(name = "created_date") private LocalDateTime createdDate;

    public Long getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
}
