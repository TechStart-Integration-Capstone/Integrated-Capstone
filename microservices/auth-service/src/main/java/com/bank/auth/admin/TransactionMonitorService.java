package com.bank.auth.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
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
                      BigDecimal amount, String currency, String status) {}

    @Transactional(readOnly = true)
    public List<Row> today() {
        LocalDate date = LocalDate.now(clock.withZone(ZONE));
        // Ledger DATETIME2 timestamps are UTC, matching the schema's SYSUTCDATETIME default.
        LocalDateTime start = date.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end = date.plusDays(1).atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return jdbc.query("""
                SELECT t.transaction_id, t.reference_no, t.transaction_date, a.account_number,
                       t.transaction_type,
                       COALESCE(t.operation,
                           (SELECT TOP (1) JSON_VALUE(CASE WHEN ISJSON(o.payload) = 1 THEN o.payload ELSE N'{}' END, '$.operation')
                            FROM dbo.OUTBOX_EVENT o WHERE o.transaction_id = t.transaction_id ORDER BY o.event_id),
                           CASE WHEN t.transaction_type IN ('CREDIT', 'WELCOME_GIFT', 'TRANSFER_IN') THEN 'CREDIT'
                                WHEN t.transaction_type IN ('DEBIT', 'TRANSFER_OUT') OR LEFT(t.transaction_type, 4) = 'EXT_' THEN 'DEBIT'
                           END) AS operation,
                       t.amount, t.source_currency, t.status
                FROM dbo.LEDGER_TRANSACTION t
                JOIN dbo.ACCOUNT a ON a.account_id = t.from_account_id
                WHERE t.transaction_date >= ? AND t.transaction_date < ?
                ORDER BY t.transaction_date DESC, t.transaction_id DESC
                """, (rs, n) -> new Row(rs.getString("transaction_id"), rs.getString("reference_no"),
                rs.getTimestamp("transaction_date").toLocalDateTime().atOffset(ZoneOffset.UTC),
                rs.getString("account_number"), rs.getString("transaction_type"), rs.getString("operation"),
                rs.getBigDecimal("amount"), rs.getString("source_currency"), rs.getString("status")),
                Timestamp.valueOf(start), Timestamp.valueOf(end));
    }
}
