package com.bank.reconciliation.model.postgres;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Maps to RECONCILIATION_LOG in PostgreSQL (ledger_audit_db).
 *
 * Column notes (post Phase-1 migration):
 *  - account_id       — nullable; populated from LEDGER_MUTATION_AUDIT when available
 *  - azure_sql_status — was oracle_status; reflects Azure SQL LEDGER_TRANSACTION.status
 */
@Entity
@Table(name = "RECONCILIATION_LOG")
public class ReconciliationLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recon_id")
    private Long reconId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    /** Nullable — populated from LEDGER_MUTATION_AUDIT when an audit row exists. */
    @Column(name = "account_id")
    private Long accountId;

    /** Azure SQL LEDGER_TRANSACTION.status (was oracle_status — renamed in Phase 1). */
    @Column(name = "azure_sql_status", nullable = false, length = 30)
    private String azureSqlStatus;

    @Column(name = "postgres_status", nullable = false, length = 30)
    private String postgresStatus;

    @Column(name = "recon_status", nullable = false, length = 30)
    private String reconStatus;

    @Column(name = "recon_date", nullable = false, updatable = false)
    private LocalDateTime reconDate = LocalDateTime.now();

    public ReconciliationLog() {}

    public ReconciliationLog(Long transactionId, String azureSqlStatus, String postgresStatus, String reconStatus) {
        this(transactionId, null, azureSqlStatus, postgresStatus, reconStatus);
    }

    public ReconciliationLog(Long transactionId, Long accountId, String azureSqlStatus, String postgresStatus, String reconStatus) {
        this.transactionId   = transactionId;
        this.accountId       = accountId;
        this.azureSqlStatus  = azureSqlStatus;
        this.postgresStatus  = postgresStatus;
        this.reconStatus     = reconStatus;
        this.reconDate       = LocalDateTime.now();
    }

    public Long          getReconId()        { return reconId; }
    public Long          getTransactionId()  { return transactionId; }
    public Long          getAccountId()      { return accountId; }
    public String        getAzureSqlStatus() { return azureSqlStatus; }
    /** Kept for backward compatibility with tests and serialization. */
    public String        getOracleStatus()   { return azureSqlStatus; }
    public String        getPostgresStatus() { return postgresStatus; }
    public String        getReconStatus()    { return reconStatus; }
    public LocalDateTime getReconDate()      { return reconDate; }

    public void setAccountId(Long accountId)           { this.accountId = accountId; }
    public void setAzureSqlStatus(String status)      { this.azureSqlStatus = status; }
    public void setOracleStatus(String status)        { this.azureSqlStatus = status; }
    public void setPostgresStatus(String status)      { this.postgresStatus = status; }
    public void setReconStatus(String status)         { this.reconStatus = status; }
    public void setReconDate(LocalDateTime reconDate) { this.reconDate = reconDate; }
}
