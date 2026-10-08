package com.bank.transaction.service;

import com.bank.transaction.dto.ActivityItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * PayPink 2.0 — CQRS Transaction Activity Service (Phase 4).
 * Serves transaction history feeds for customers, resolving both debit legs and
 * incoming transfer (credit) legs with counterparty details.
 */
@Service
public class TransactionActivityService {

    private static final Logger log = LoggerFactory.getLogger(TransactionActivityService.class);
    private final JdbcTemplate jdbc;

    public TransactionActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String formatReference(long id, LocalDateTime date) {
        if (date == null) date = LocalDateTime.now();
        return "PP-" + date.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Manila"))
                .toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + String.format(Locale.ROOT, "%012d", Math.abs(id));
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> getCustomerActivity(Long customerId) {
        if (customerId == null || customerId == 0L) {
            return List.of();
        }

        String sql = """
            SELECT * FROM (
                SELECT t.transaction_id AS activity_id, t.transaction_id AS ledger_id, a.account_id, a.account_number,
                       t.amount, t.source_currency, t.transaction_type,
                       COALESCE(
                           CASE t.transaction_type
                               WHEN 'LOAN_DISBURSEMENT' THEN 'CREDIT'
                               WHEN 'LOAN_REPAYMENT' THEN 'DEBIT'
                           END,
                           (SELECT TOP 1 JSON_VALUE(o.payload, '$.operation')
                            FROM dbo.OUTBOX_EVENT o
                            WHERE o.transaction_id = t.transaction_id
                            ORDER BY o.event_id),
                           CASE WHEN t.transaction_type IN ('DEBIT','CREDIT') THEN t.transaction_type END
                       ) AS operation,
                       t.status, t.transaction_date,
                       c.first_name + ' ' + c.last_name AS counterparty_name,
                       target.account_number AS counterparty_account
                FROM dbo.LEDGER_TRANSACTION t
                JOIN dbo.ACCOUNT a ON a.account_id = CASE
                    WHEN t.transaction_type = 'LOAN_DISBURSEMENT' THEN t.to_account_id
                    ELSE t.from_account_id
                END
                LEFT JOIN dbo.ACCOUNT target ON target.account_id = t.to_account_id
                    AND t.transaction_type IN ('TRANSFER_OUT','TRANSFER_IN','P2P_REMITTANCE')
                LEFT JOIN dbo.CUSTOMER c ON c.customer_id = target.customer_id
                WHERE a.customer_id = ?

                UNION ALL

                SELECT -t.transaction_id, t.transaction_id, a.account_id, a.account_number,
                       t.amount, t.target_currency,
                       'TRANSFER_IN', 'CREDIT', t.status, t.transaction_date,
                       c.first_name + ' ' + c.last_name, source.account_number
                FROM dbo.LEDGER_TRANSACTION t
                JOIN dbo.ACCOUNT a ON a.account_id = t.to_account_id
                JOIN dbo.ACCOUNT source ON source.account_id = t.from_account_id
                LEFT JOIN dbo.CUSTOMER c ON c.customer_id = source.customer_id
                WHERE t.transaction_type = 'P2P_REMITTANCE' AND a.customer_id = ?
            ) activity
            ORDER BY transaction_date DESC, ledger_id DESC, activity_id DESC
            OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY
        """;

        return jdbc.query(sql, (rs, rowNum) -> {
            String type = rs.getString("transaction_type");
            LocalDateTime date = rs.getTimestamp("transaction_date").toLocalDateTime();
            long ledgerId = rs.getLong("ledger_id");
            String ref = formatReference(ledgerId, date);

            return new ActivityItem(
                    rs.getLong("activity_id"),
                    rs.getLong("account_id"),
                    rs.getString("account_number"),
                    rs.getBigDecimal("amount"),
                    rs.getString("source_currency"),
                    type,
                    rs.getString("operation"),
                    ref,
                    rs.getString("status"),
                    date,
                    rs.getString("counterparty_name"),
                    rs.getString("counterparty_account")
            );
        }, customerId, customerId);
    }
}
