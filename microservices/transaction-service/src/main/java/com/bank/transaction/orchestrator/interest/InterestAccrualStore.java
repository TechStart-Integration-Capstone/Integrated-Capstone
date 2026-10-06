package com.bank.transaction.orchestrator.interest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** PostgreSQL access is strictly SELECT/INSERT. A daily batch commits independently of Azure SQL. */
public class InterestAccrualStore {
    public record Accrual(long accountId, BigDecimal balance, BigDecimal rate, BigDecimal amount) {}
    public record MonthlyTotal(long accountId, BigDecimal amount) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public InterestAccrualStore(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public boolean completed(LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM interest_accrual_batch WHERE business_date = ?)",
                Boolean.class, Date.valueOf(date)));
    }

    public void append(LocalDate date, List<Accrual> rows) {
        transaction.executeWithoutResult(status -> {
            jdbc.batchUpdate("""
                    INSERT INTO interest_accrual (account_id, business_date, eod_balance, rate, interest_amount)
                    VALUES (?, ?, ?, ?, ?)
                    """, rows, 500, (ps, row) -> {
                ps.setLong(1, row.accountId());
                ps.setDate(2, Date.valueOf(date));
                ps.setBigDecimal(3, row.balance());
                ps.setBigDecimal(4, row.rate());
                ps.setBigDecimal(5, row.amount());
            });
            jdbc.update("INSERT INTO interest_accrual_batch (business_date, account_count) VALUES (?, ?)",
                    Date.valueOf(date), rows.size());
        });
    }

    public Set<LocalDate> completedDates(LocalDate start, LocalDate end) {
        return new HashSet<>(jdbc.query("""
                SELECT business_date FROM interest_accrual_batch WHERE business_date BETWEEN ? AND ?
                """, (rs, n) -> rs.getDate(1).toLocalDate(), Date.valueOf(start), Date.valueOf(end)));
    }

    public List<MonthlyTotal> monthlyTotals(LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT account_id, ROUND(SUM(interest_amount), 2) AS total_monthly_interest
                FROM interest_accrual WHERE business_date BETWEEN ? AND ?
                GROUP BY account_id ORDER BY account_id
                """, (rs, n) -> new MonthlyTotal(rs.getLong(1), rs.getBigDecimal(2)),
                Date.valueOf(start), Date.valueOf(end));
    }
}
