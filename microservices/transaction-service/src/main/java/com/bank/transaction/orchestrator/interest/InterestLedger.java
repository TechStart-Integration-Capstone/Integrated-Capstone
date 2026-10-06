package com.bank.transaction.orchestrator.interest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

/** All methods are called inside locked(), keeping balance, GL, ledger and outbox atomic. */
public class InterestLedger {
    public record Account(long id, long customerId, String type, String currency,
                          BigDecimal balance, BigDecimal contractRate) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;

    public InterestLedger(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mapper = mapper;
    }

    public <T> T locked(Supplier<T> work) {
        return transaction.execute(status -> {
            Integer result = jdbc.queryForObject("""
                    IF @@TRANCOUNT = 0 BEGIN TRANSACTION;
                    DECLARE @result INT;
                    EXEC @result = sys.sp_getapplock @Resource = 'paypink-interest-eod',
                        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
                    SELECT @result;
                    """, Integer.class);
            if (result == null || result < 0)
                throw new IllegalStateException("Cannot acquire interest EOD lock (SQL Server result " + result + ")");
            return work.get();
        });
    }

    public List<Account> activeAccounts() {
        // Hold the source balances stable until the complete PostgreSQL snapshot has committed.
        return jdbc.query("""
                SELECT account_id, customer_id, account_type, currency, current_balance, interest_rate
                FROM dbo.ACCOUNT WITH (UPDLOCK, HOLDLOCK)
                WHERE status = 'ACTIVE' AND account_type IN ('SAVINGS', 'SAVINGS_ACCOUNT', 'LOAN') ORDER BY account_id
                """, (rs, n) -> new Account(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6)));
    }

    public long startJob(LocalDate date, String name) {
        List<Long> existing = jdbc.queryForList(
                "SELECT job_run_id FROM dbo.EOD_JOB_RUN WHERE business_date = ? AND job_name = ?",
                Long.class, Date.valueOf(date), name);
        if (!existing.isEmpty()) {
            jdbc.update("UPDATE dbo.EOD_JOB_RUN SET status = 'RUNNING', ended_at = NULL WHERE job_run_id = ?",
                    existing.get(0));
            return existing.get(0);
        }
        return jdbc.queryForObject("""
                INSERT INTO dbo.EOD_JOB_RUN (business_date, job_name, status, started_at)
                OUTPUT INSERTED.job_run_id VALUES (?, ?, 'RUNNING', SYSUTCDATETIME())
                """, Long.class, Date.valueOf(date), name);
    }

    public void finishJob(long id) {
        jdbc.update("UPDATE dbo.EOD_JOB_RUN SET status = 'SUCCESS', ended_at = SYSUTCDATETIME() WHERE job_run_id = ?", id);
    }

    public boolean postingComplete(LocalDate end) {
        return !jdbc.queryForList("""
                SELECT job_run_id FROM dbo.EOD_JOB_RUN
                WHERE job_name = 'EOD_INTEREST_POSTING' AND business_date = ? AND status = 'SUCCESS'
                """, Long.class, Date.valueOf(end)).isEmpty();
    }

    public boolean post(InterestAccrualStore.MonthlyTotal total, LocalDate start, LocalDate end, long jobId) {
        Account account = jdbc.queryForObject("""
                SELECT account_id, customer_id, account_type, currency, current_balance, interest_rate
                FROM dbo.ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_id = ?
                """, (rs, n) -> new Account(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6)), total.accountId());
        if (account == null) throw new IllegalStateException("Accrued account is missing: " + total.accountId());
        if ("LOAN".equals(account.type())) return false; // loan capitalization is outside this policy
        if (!InterestPolicy.isSavings(account.type()))
            throw new IllegalStateException("Accrued account type changed: " + account.id());

        List<BigDecimal> posted = jdbc.query("""
                SELECT amount FROM dbo.GL_ENTRY
                WHERE account_id = ? AND posting_type = 'MONTHLY_INTEREST' AND period_end = ?
                """, (rs, n) -> rs.getBigDecimal(1), account.id(), Date.valueOf(end));
        if (!posted.isEmpty()) {
            if (posted.get(0).compareTo(total.amount()) != 0)
                throw new IllegalStateException("Posted interest differs from immutable accruals for " + account.id());
            return false;
        }
        if (total.amount().signum() < 0) throw new IllegalArgumentException("Negative monthly interest");

        Long transactionId = null;
        if (total.amount().signum() > 0) {
            jdbc.update("UPDATE dbo.ACCOUNT SET current_balance = current_balance + ? WHERE account_id = ?",
                    total.amount(), account.id());
            String reference = "INT-" + end + "-" + account.id();
            transactionId = jdbc.queryForObject("""
                    INSERT INTO dbo.LEDGER_TRANSACTION
                        (from_account_id, amount, source_currency, target_currency, transaction_type, reference_no, status)
                    OUTPUT INSERTED.transaction_id VALUES (?, ?, ?, ?, 'INTEREST_CREDIT', ?, 'SUCCESS')
                    """, Long.class, account.id(), total.amount(), account.currency(), account.currency(), reference);
            var payload = new LinkedHashMap<String, Object>();
            payload.put("transactionId", transactionId);
            payload.put("referenceNo", reference);
            payload.put("accountId", account.id());
            payload.put("customerId", account.customerId());
            payload.put("operation", "CREDIT");
            payload.put("transactionType", "INTEREST_CREDIT");
            payload.put("amount", total.amount().toPlainString());
            payload.put("currency", account.currency());
            payload.put("beforeBalance", account.balance().toPlainString());
            payload.put("afterBalance", account.balance().add(total.amount()).toPlainString());
            payload.put("businessDate", end.toString());
            try {
                jdbc.update("""
                        INSERT INTO dbo.OUTBOX_EVENT (transaction_id, event_type, payload, status)
                        VALUES (?, 'TRANSACTION_SUCCESS', ?, 'PENDING')
                        """, transactionId, mapper.writeValueAsString(payload));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Cannot serialize interest posting", ex);
            }
        }
        // A zero-value GL record seals the period without violating the ledger's positive amount constraint.
        jdbc.update("""
                INSERT INTO dbo.GL_ENTRY (account_id, amount, entry_type, posting_type, description,
                    business_date, period_start, period_end, job_run_id, transaction_id)
                VALUES (?, ?, 'CREDIT', 'MONTHLY_INTEREST', ?, ?, ?, ?, ?, ?)
                """, account.id(), total.amount(), "Monthly Interest Posting - Period End: " + end,
                Date.valueOf(end), Date.valueOf(start), Date.valueOf(end), jobId, transactionId);
        return true;
    }
}
