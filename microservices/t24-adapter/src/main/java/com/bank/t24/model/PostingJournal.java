package com.bank.t24.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "POSTING_JOURNAL", schema = "t24")
public class PostingJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_id")
    private Long journalId;

    @Column(name = "reference_no", nullable = false, unique = true, length = 64)
    private String referenceNo;

    @Column(name = "debit_account_id", nullable = false)
    private Long debitAccountId;

    @Column(name = "credit_account_id", nullable = false)
    private Long creditAccountId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PHP";

    @Column(name = "debit_balance_before", nullable = false, precision = 18, scale = 4)
    private BigDecimal debitBalanceBefore;

    @Column(name = "debit_balance_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal debitBalanceAfter;

    @Column(name = "credit_balance_before", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditBalanceBefore;

    @Column(name = "credit_balance_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditBalanceAfter;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "POSTED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PostingJournal() {}

    public PostingJournal(String referenceNo, Long debitAccountId, Long creditAccountId, BigDecimal amount,
                          String currency, BigDecimal debitBalanceBefore, BigDecimal debitBalanceAfter,
                          BigDecimal creditBalanceBefore, BigDecimal creditBalanceAfter) {
        this.referenceNo = referenceNo;
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.amount = amount;
        this.currency = (currency != null && !currency.isBlank()) ? currency : "PHP";
        this.debitBalanceBefore = debitBalanceBefore;
        this.debitBalanceAfter = debitBalanceAfter;
        this.creditBalanceBefore = creditBalanceBefore;
        this.creditBalanceAfter = creditBalanceAfter;
        this.status = "POSTED";
        this.createdAt = LocalDateTime.now();
    }

    public Long getJournalId() { return journalId; }
    public void setJournalId(Long journalId) { this.journalId = journalId; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public Long getDebitAccountId() { return debitAccountId; }
    public void setDebitAccountId(Long debitAccountId) { this.debitAccountId = debitAccountId; }

    public Long getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(Long creditAccountId) { this.creditAccountId = creditAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getDebitBalanceBefore() { return debitBalanceBefore; }
    public void setDebitBalanceBefore(BigDecimal debitBalanceBefore) { this.debitBalanceBefore = debitBalanceBefore; }

    public BigDecimal getDebitBalanceAfter() { return debitBalanceAfter; }
    public void setDebitBalanceAfter(BigDecimal debitBalanceAfter) { this.debitBalanceAfter = debitBalanceAfter; }

    public BigDecimal getCreditBalanceBefore() { return creditBalanceBefore; }
    public void setCreditBalanceBefore(BigDecimal creditBalanceBefore) { this.creditBalanceBefore = creditBalanceBefore; }

    public BigDecimal getCreditBalanceAfter() { return creditBalanceAfter; }
    public void setCreditBalanceAfter(BigDecimal creditBalanceAfter) { this.creditBalanceAfter = creditBalanceAfter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
