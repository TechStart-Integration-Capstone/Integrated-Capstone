package com.bank.transaction.service;

import com.bank.transaction.dto.AdminTransactionRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PayPink 2.0 — Admin Transaction Monitor Feed Service (Phase 4).
 * Queries today's real-time ledger and saga movements for the Operations Desk.
 */
@Service
public class AdminTransactionMonitorService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public AdminTransactionMonitorService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    public AdminTransactionMonitorService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
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

    @Transactional(readOnly = true)
    public List<AdminTransactionRow> today() {
        ZonedDateTime nowManila = clock.instant().atZone(ZONE);
        ZonedDateTime startOfDayManila = nowManila.toLocalDate().atStartOfDay(ZONE);
        Instant startUtc = startOfDayManila.toInstant();
        Instant endUtc = startOfDayManila.plusDays(1).toInstant();

        Timestamp startTs = Timestamp.from(startUtc);
        Timestamp endTs = Timestamp.from(endUtc);

        String sql = """
            SELECT
                COALESCE(r.reference_no, t.reference_no, 'TX-' + CAST(t.transaction_id AS NVARCHAR(30))) AS reference_no,
                COALESCE(r.created_at, t.transaction_date) AS tx_time,
                COALESCE(src.account_number, CAST(r.source_account_id AS NVARCHAR(30)), 'N/A') AS account_number,
                COALESCE(r.transaction_type, t.transaction_type, 'TRANSFER') AS transaction_type,
                CASE
                    WHEN r.remittance_id IS NOT NULL THEN 'DEBIT'
                    WHEN t.transaction_type IN ('CREDIT', 'TRANSFER_IN', 'WELCOME_GIFT') THEN 'CREDIT'
                    ELSE 'DEBIT'
                END AS operation,
                COALESCE(r.amount, t.amount, 0) AS amount,
                COALESCE(r.currency, t.source_currency, 'PHP') AS currency,
                COALESCE(r.status, t.status, 'Initiated') AS raw_status,
                r.internal_status,
                r.current_service,
                r.reason,
                tgt.account_number AS target_account_number,
                t.transaction_id
            FROM dbo.REMITTANCE r
            FULL OUTER JOIN dbo.LEDGER_TRANSACTION t
                ON r.reference_no = t.reference_no
            LEFT JOIN dbo.ACCOUNT src
                ON src.account_id = COALESCE(r.source_account_id, t.from_account_id)
            LEFT JOIN dbo.ACCOUNT tgt
                ON tgt.account_id = COALESCE(r.target_account_id, t.to_account_id)
            WHERE (r.created_at >= ? AND r.created_at < ?)
               OR (t.transaction_date >= ? AND t.transaction_date < ?)
            ORDER BY tx_time DESC
            OFFSET 0 ROWS FETCH NEXT 500 ROWS ONLY
        """;

        return jdbc.query(sql, (rs, rowNum) -> {
            String refNo = rs.getString("reference_no");
            Timestamp txTs = rs.getTimestamp("tx_time");
            OffsetDateTime odt = txTs != null ? txTs.toInstant().atZone(ZONE).toOffsetDateTime() : nowManila.toOffsetDateTime();
            String accNum = rs.getString("account_number");
            String txType = rs.getString("transaction_type");
            String op = rs.getString("operation");
            BigDecimal amt = rs.getBigDecimal("amount");
            String curr = rs.getString("currency");
            String rawStat = rs.getString("raw_status");
            String mappedStat = mapToDashboardStatus(rawStat);
            String internalStat = rs.getString("internal_status");
            String currService = rs.getString("current_service");
            String reason = rs.getString("reason");
            String targetAcc = rs.getString("target_account_number");
            Long txId = rs.getObject("transaction_id", Long.class);
            String displayTxId = txId != null ? String.valueOf(txId) : refNo;

            return new AdminTransactionRow(
                    displayTxId,
                    refNo,
                    odt,
                    accNum,
                    txType,
                    op,
                    amt != null ? amt : BigDecimal.ZERO,
                    curr != null ? curr : "PHP",
                    mappedStat,
                    internalStat,
                    currService,
                    reason,
                    targetAcc
            );
        }, startTs, endTs, startTs, endTs);
    }
}
