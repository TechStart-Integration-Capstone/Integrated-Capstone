package com.bank.auth.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionMonitorService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public TransactionMonitorService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    TransactionMonitorService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Row(String transactionId, String referenceNo, OffsetDateTime transactionDate,
                      String accountNumber, String transactionType, String operation,
                      BigDecimal amount, String currency, String status,
                      String internalStatus, String currentService, String reason,
                      String targetAccountNumber) {
        public Row(String transactionId, String referenceNo, OffsetDateTime transactionDate,
                   String accountNumber, String transactionType, String operation,
                   BigDecimal amount, String currency, String status) {
            this(transactionId, referenceNo, transactionDate, accountNumber, transactionType,
                 operation, amount, currency, status, null, null, null, null);
        }
    }

    public static String mapToDashboardStatus(String rawStatus) {
        if (rawStatus == null) return "Initiated";
        String upper = rawStatus.trim().toUpperCase();
        return switch (upper) {
            case "INITIATED", "VALIDATED", "AUTHENTICATED" -> "Initiated";
            case "AUTHORIZED" -> "Authorized";
            case "RESERVED", "PENDING_CORE", "PENDING" -> "Reserved";
            case "PROCESSING", "T24_POSTED", "IN_PROGRESS" -> "Processing";
            case "POSTED", "LEDGER_UPDATE", "NOTIFICATION", "RECONCILIATION", "SUCCESS", "COMPLETED" -> "Posted";
            case "FAILED", "REJECTED", "INSUFFICIENT_FUNDS", "LIMIT_EXCEEDED", "FRAUD_REJECTED", "ERROR" -> "Failed";
            case "CANCELLED", "CANCELED", "ROLLBACK", "REVERSED" -> "Cancelled";
            default -> "Processing";
        };
    }

    private boolean isRemittanceTablePresent() {
        try {
            jdbc.execute("SELECT TOP (1) 1 FROM dbo.REMITTANCE");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<Row> today() {
        LocalDate date = LocalDate.now(clock.withZone(ZONE));
        // Ledger DATETIME2 timestamps are UTC, matching the schema's GETUTCDATE default.
        LocalDateTime start = date.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end = date.plusDays(1).atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        if (!isRemittanceTablePresent()) {
            return queryLedgerTransactionsOnly(start, end);
        }

        List<Row> rows = new ArrayList<>();

        // 1. Funds transfers / remittances from dbo.REMITTANCE (within bank funds transfers)
        List<Row> remittanceRows = jdbc.query("""
                SELECT COALESCE(CAST(lt.transaction_id AS NVARCHAR(30)), CAST(r.remittance_id AS NVARCHAR(30))) AS transaction_id,
                       r.reference_no,
                       r.created_at AS transaction_date,
                       sa.account_number AS source_account_number,
                       ta.account_number AS target_account_number,
                       COALESCE(r.transaction_type, 'TRANSFER') AS transaction_type,
                       'DEBIT' AS operation,
                       r.amount,
                       r.currency,
                       r.status,
                       COALESCE(r.internal_status, CASE WHEN r.status IN ('POSTED', 'Posted') THEN 'LEDGER_UPDATE'
                                                        WHEN r.status IN ('RESERVED', 'Reserved', 'PENDING_CORE') THEN 'AUTHORIZED'
                                                        WHEN r.status IN ('PROCESSING', 'Processing', 'T24_POSTED') THEN 'POSTED'
                                                        WHEN r.status IN ('REJECTED', 'FAILED', 'Failed') THEN 'FRAUD_CHECK'
                                                        ELSE 'INITIATED' END) AS internal_status,
                       COALESCE(r.current_service, CASE WHEN r.status IN ('POSTED', 'Posted') THEN 'transaction-service'
                                                        WHEN r.status IN ('PROCESSING', 'Processing', 'T24_POSTED') THEN 't24-adapter'
                                                        ELSE 'transaction-service' END) AS current_service,
                       r.reason
                FROM dbo.REMITTANCE r
                JOIN dbo.ACCOUNT sa ON sa.account_id = r.source_account_id
                LEFT JOIN dbo.ACCOUNT ta ON ta.account_id = r.target_account_id
                LEFT JOIN dbo.LEDGER_TRANSACTION lt ON lt.reference_no = r.reference_no
                WHERE r.created_at >= ? AND r.created_at < ?
                  AND sa.account_type <> 'STRESS_TEST_ACCOUNT'
                  AND (ta.account_type IS NULL OR ta.account_type <> 'STRESS_TEST_ACCOUNT')
                """, (rs, n) -> new Row(
                rs.getString("transaction_id"),
                rs.getString("reference_no"),
                rs.getTimestamp("transaction_date").toLocalDateTime().atOffset(ZoneOffset.UTC),
                rs.getString("source_account_number"),
                rs.getString("transaction_type"),
                rs.getString("operation"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                mapToDashboardStatus(rs.getString("status")),
                rs.getString("internal_status"),
                rs.getString("current_service"),
                rs.getString("reason"),
                rs.getString("target_account_number")),
                Timestamp.valueOf(start), Timestamp.valueOf(end));

        rows.addAll(remittanceRows);

        // 2. Non-remittance ledger transactions from dbo.LEDGER_TRANSACTION (loan disbursements, repayments, gifts, etc.)
        List<Row> ledgerRows = jdbc.query("""
                SELECT t.transaction_id, t.reference_no, t.transaction_date, a.account_number,
                       t.transaction_type,
                       COALESCE(CASE t.transaction_type WHEN 'LOAN_DISBURSEMENT' THEN 'CREDIT'
                                                        WHEN 'LOAN_REPAYMENT' THEN 'DEBIT' END,
                           (SELECT TOP (1) JSON_VALUE(CASE WHEN ISJSON(o.payload) = 1 THEN o.payload ELSE N'{}' END, '$.operation')
                            FROM dbo.OUTBOX_EVENT o WHERE o.transaction_id = t.transaction_id ORDER BY o.event_id),
                           CASE WHEN t.transaction_type IN ('CREDIT', 'WELCOME_GIFT', 'TRANSFER_IN') THEN 'CREDIT'
                                WHEN t.transaction_type IN ('DEBIT', 'TRANSFER_OUT') OR LEFT(t.transaction_type, 4) = 'EXT_' THEN 'DEBIT'
                           END) AS operation,
                       t.amount, t.source_currency, t.status, t.failure_reason
                FROM dbo.LEDGER_TRANSACTION t
                JOIN dbo.ACCOUNT a ON a.account_id = CASE WHEN t.transaction_type = 'LOAN_DISBURSEMENT'
                                                          THEN t.to_account_id ELSE t.from_account_id END
                WHERE t.transaction_date >= ? AND t.transaction_date < ?
                  AND a.account_type <> 'STRESS_TEST_ACCOUNT'
                  AND NOT EXISTS (SELECT 1 FROM dbo.ACCOUNT target
                                  WHERE target.account_id = t.to_account_id
                                    AND target.account_type = 'STRESS_TEST_ACCOUNT')
                  AND NOT EXISTS (SELECT 1 FROM dbo.REMITTANCE rem WHERE rem.reference_no = t.reference_no)
                """, (rs, n) -> new Row(
                rs.getString("transaction_id"),
                rs.getString("reference_no"),
                rs.getTimestamp("transaction_date").toLocalDateTime().atOffset(ZoneOffset.UTC),
                rs.getString("account_number"),
                rs.getString("transaction_type"),
                rs.getString("operation"),
                rs.getBigDecimal("amount"),
                rs.getString("source_currency"),
                mapToDashboardStatus(rs.getString("status")),
                "LEDGER_UPDATE",
                "transaction-service",
                rs.getString("failure_reason"),
                null),
                Timestamp.valueOf(start), Timestamp.valueOf(end));

        rows.addAll(ledgerRows);

        rows.sort((a, b) -> {
            int cmp = b.transactionDate().compareTo(a.transactionDate());
            if (cmp != 0) return cmp;
            return String.valueOf(b.transactionId()).compareTo(String.valueOf(a.transactionId()));
        });

        return rows;
    }

    private List<Row> queryLedgerTransactionsOnly(LocalDateTime start, LocalDateTime end) {
        return jdbc.query("""
                SELECT t.transaction_id, t.reference_no, t.transaction_date, a.account_number,
                       t.transaction_type,
                       COALESCE(CASE t.transaction_type WHEN 'LOAN_DISBURSEMENT' THEN 'CREDIT'
                                                        WHEN 'LOAN_REPAYMENT' THEN 'DEBIT' END,
                           (SELECT TOP (1) JSON_VALUE(CASE WHEN ISJSON(o.payload) = 1 THEN o.payload ELSE N'{}' END, '$.operation')
                            FROM dbo.OUTBOX_EVENT o WHERE o.transaction_id = t.transaction_id ORDER BY o.event_id),
                           CASE WHEN t.transaction_type IN ('CREDIT', 'WELCOME_GIFT', 'TRANSFER_IN') THEN 'CREDIT'
                                WHEN t.transaction_type IN ('DEBIT', 'TRANSFER_OUT') OR LEFT(t.transaction_type, 4) = 'EXT_' THEN 'DEBIT'
                           END) AS operation,
                       t.amount, t.source_currency, t.status
                FROM dbo.LEDGER_TRANSACTION t
                JOIN dbo.ACCOUNT a ON a.account_id = CASE WHEN t.transaction_type = 'LOAN_DISBURSEMENT'
                                                          THEN t.to_account_id ELSE t.from_account_id END
                WHERE t.transaction_date >= ? AND t.transaction_date < ?
                  AND a.account_type <> 'STRESS_TEST_ACCOUNT'
                  AND NOT EXISTS (SELECT 1 FROM dbo.ACCOUNT target
                                  WHERE target.account_id = t.to_account_id
                                    AND target.account_type = 'STRESS_TEST_ACCOUNT')
                ORDER BY t.transaction_date DESC, t.transaction_id DESC
                """, (rs, n) -> new Row(rs.getString("transaction_id"), rs.getString("reference_no"),
                rs.getTimestamp("transaction_date").toLocalDateTime().atOffset(ZoneOffset.UTC),
                rs.getString("account_number"), rs.getString("transaction_type"), rs.getString("operation"),
                rs.getBigDecimal("amount"), rs.getString("source_currency"), rs.getString("status")),
                Timestamp.valueOf(start), Timestamp.valueOf(end));
    }
}
