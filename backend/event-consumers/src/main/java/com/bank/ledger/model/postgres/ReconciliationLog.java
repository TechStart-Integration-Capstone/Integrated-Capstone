package com.bank.ledger.model.postgres;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "RECONCILIATION_LOG", uniqueConstraints = {
    @UniqueConstraint(name = "uq_recon_tx_account", columnNames = {"transaction_id", "account_id"})
})
public class ReconciliationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recon_id")
    private Long reconId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId = 0L;

    @Column(name = "oracle_status", nullable = false, length = 30)
    private String oracleStatus; // 'SUCCESS', 'FAILED', 'COMMITTED'

    @Column(name = "postgres_status", nullable = false, length = 30)
    private String postgresStatus; // 'COMMITTED', 'PENDING', 'MISSING'

    @Column(name = "recon_status", nullable = false, length = 30)
    private String reconStatus; // 'MATCHED', 'DRIFT_DETECTED'

    @Column(name = "mismatch_fields", length = 200)
    private String mismatchFields;

    @Column(name = "check_count", nullable = false)
    private Integer checkCount = 1;

    @Column(name = "last_checked_at", nullable = false)
    private LocalDateTime lastCheckedAt = LocalDateTime.now();

    @Column(name = "recon_date", nullable = false, updatable = false)
    private LocalDateTime reconDate = LocalDateTime.now();

    public ReconciliationLog() {}

    public ReconciliationLog(Long transactionId, String oracleStatus, String postgresStatus, String reconStatus) {
        this.transactionId = transactionId;
        this.accountId = 0L;
        this.oracleStatus = oracleStatus;
        this.postgresStatus = postgresStatus;
        this.reconStatus = reconStatus;
        this.checkCount = 1;
        this.lastCheckedAt = LocalDateTime.now();
        this.reconDate = LocalDateTime.now();
    }

    public ReconciliationLog(Long transactionId, Long accountId, String oracleStatus, String postgresStatus, String reconStatus, String mismatchFields) {
        this.transactionId = transactionId;
        this.accountId = accountId != null ? accountId : 0L;
        this.oracleStatus = oracleStatus;
        this.postgresStatus = postgresStatus;
        this.reconStatus = reconStatus;
        this.mismatchFields = mismatchFields;
        this.checkCount = 1;
        this.lastCheckedAt = LocalDateTime.now();
        this.reconDate = LocalDateTime.now();
    }

    public Long getReconId() { return reconId; }
    public void setReconId(Long reconId) { this.reconId = reconId; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getOracleStatus() { return oracleStatus; }
    public void setOracleStatus(String oracleStatus) { this.oracleStatus = oracleStatus; }

    public String getPostgresStatus() { return postgresStatus; }
    public void setPostgresStatus(String postgresStatus) { this.postgresStatus = postgresStatus; }

    public String getReconStatus() { return reconStatus; }
    public void setReconStatus(String reconStatus) { this.reconStatus = reconStatus; }

    public String getMismatchFields() { return mismatchFields; }
    public void setMismatchFields(String mismatchFields) { this.mismatchFields = mismatchFields; }

    public Integer getCheckCount() { return checkCount; }
    public void setCheckCount(Integer checkCount) { this.checkCount = checkCount; }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

    public LocalDateTime getReconDate() { return reconDate; }
    public void setReconDate(LocalDateTime reconDate) { this.reconDate = reconDate; }
}
