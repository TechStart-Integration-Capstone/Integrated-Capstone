package com.bank.transaction.orchestrator.interest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Native PostgreSQL + SQL Server tests. scripts/test_interest.ps1 supplies disposable databases. */
@EnabledIfEnvironmentVariable(named = "INTEREST_TEST_DATABASES", matches = "disposable")
class InterestDatabaseTest {
    private JdbcTemplate sql;
    private JdbcTemplate pg;
    private InterestLedger ledger;
    private InterestAccrualStore audit;
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @BeforeEach void setup() throws Exception {
        String sqlUrl = System.getenv("INTEREST_TEST_SQL_URL");
        String pgUrl = System.getenv("INTEREST_TEST_PG_URL");
        // Refuse application databases, even if this opt-in test is misconfigured.
        if (!sqlUrl.contains("databaseName=interest_test;") || !pgUrl.endsWith("/interest_test"))
            throw new IllegalArgumentException("Only the disposable interest_test database is allowed");
        var primary = new DriverManagerDataSource(sqlUrl, "sa", System.getenv("INTEREST_TEST_PASSWORD"));
        var secondary = new DriverManagerDataSource(pgUrl, "postgres", System.getenv("INTEREST_TEST_PASSWORD"));
        sql = new JdbcTemplate(primary);
        pg = new JdbcTemplate(secondary);
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("scripts"))) root = root.getParent();
        String bootstrap = Files.readString(root.resolve("microservices/transaction-service/src/main/resources/schema-azuresql.sql"));
        for (String batch : bootstrap.split("(?im)^GO\\s*$")) if (!batch.isBlank()) sql.execute(batch);
        sql.execute(Files.readString(root.resolve("scripts/migrate_interest_azuresql.sql"))); // migration is rerunnable
        sql.update("UPDATE dbo.ACCOUNT SET status = 'INACTIVE'");
        pg.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        String migration = Files.readString(root.resolve("scripts/migrate_interest_postgres.sql"));
        pg.execute(migration);
        pg.execute(migration);
        ledger = new InterestLedger(sql, new TransactionTemplate(new DataSourceTransactionManager(primary)), new ObjectMapper());
        audit = new InterestAccrualStore(pg, new TransactionTemplate(new DataSourceTransactionManager(secondary)));
    }

    private InterestEodService service(LocalDate today, LocalDate start) {
        return new InterestEodService(ledger, audit, start,
                Clock.fixed(today.atTime(23, 59, 59).atZone(ZoneId.of("Asia/Manila")).toInstant(), ZoneId.of("Asia/Manila")));
    }

    private long account(String type, String balance, String rate) {
        return sql.queryForObject("""
                INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, current_balance, interest_rate)
                OUTPUT INSERTED.account_id VALUES (1, ?, ?, ?, ?)
                """, Long.class, java.util.UUID.randomUUID().toString().substring(0, 30),
                type, new BigDecimal(balance), new BigDecimal(rate));
    }

    private BigDecimal balance(long id) {
        return sql.queryForObject("SELECT current_balance FROM dbo.ACCOUNT WHERE account_id = ?", BigDecimal.class, id);
    }

    @Test void fullMonthPreservesPrecisionIncludesClosingDayAndCreditsSavingsOnly() throws Exception {
        long savings = account("SAVINGS_ACCOUNT", "10000", "0");
        long loan = account("LOAN", "10000", "0.18");
        account("CHECKING", "10000", "0");
        for (LocalDate date = END.withDayOfMonth(1); !date.isAfter(END); date = date.plusDays(1)) {
            service(date, END.withDayOfMonth(1)).accrue(date);
        }
        assertThat(balance(savings)).isEqualByComparingTo("10000");
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_accrual", Integer.class)).isEqualTo(62);
        var worker = service(END, END.withDayOfMonth(1));
        assertThat(worker.postMonth(END).accounts()).isEqualTo(1);
        assertThat(worker.postMonth(END).replayed()).isTrue();
        assertThat(balance(savings)).isEqualByComparingTo("10033.97");
        assertThat(balance(loan)).isEqualByComparingTo("10000");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.GL_ENTRY", Integer.class)).isEqualTo(1);
        String payload = sql.queryForObject("SELECT payload FROM dbo.OUTBOX_EVENT", String.class);
        var event = new ObjectMapper().readTree(payload);
        assertThat(event.path("operation").asText()).isEqualTo("CREDIT");
        assertThat(event.path("beforeBalance").asText()).isEqualTo("10000.0000");
        assertThat(event.path("afterBalance").asText()).isEqualTo("10033.9700");
    }

    @Test void postgresBlocksMutationTruncationLateInsertsAndDuplicateDays() {
        long id = account("SAVINGS", "1000", "0");
        var worker = service(END, END);
        worker.accrue(END);
        assertThat(worker.accrue(END).replayed()).isTrue();
        assertThat(pg.update("UPDATE interest_accrual SET interest_amount = 100")).isZero();
        assertThat(pg.update("DELETE FROM interest_accrual")).isZero();
        assertThat(pg.update("UPDATE interest_accrual_batch SET account_count = 0")).isZero();
        assertThat(pg.update("DELETE FROM interest_accrual_batch")).isZero();
        assertThatThrownBy(() -> pg.execute("TRUNCATE interest_accrual")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> pg.execute("TRUNCATE interest_accrual_batch")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> audit.append(END, List.of(new InterestAccrualStore.Accrual(id, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))))
                .hasMessageContaining("already complete");
        pg.update("INSERT INTO interest_accrual(account_id,business_date,eod_balance,rate,interest_amount) VALUES (?, ?, 1000, .025, .068493)", id, Date.valueOf(END.minusDays(1)));
        assertThatThrownBy(() -> pg.update("INSERT INTO interest_accrual(account_id,business_date,eod_balance,rate,interest_amount) VALUES (?, ?, 1000, .025, .068493)", id, Date.valueOf(END.minusDays(1))))
                .hasMessageContaining("uq_account_business_date");
    }

    @Test void postgresFailureRollsBackEntireDailyBatch() {
        account("SAVINGS", "1000", "0");
        long bad = account("SAVINGS", "10000", "0");
        pg.execute("ALTER TABLE interest_accrual ADD CONSTRAINT injected_failure CHECK (account_id <> " + bad + ")");
        assertThatThrownBy(() -> service(END, END).accrue(END)).isInstanceOf(RuntimeException.class);
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_accrual", Integer.class)).isZero();
        assertThat(audit.completed(END)).isFalse();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.EOD_JOB_RUN", Integer.class)).isZero();
    }

    @Test void azureFailureAfterAuditCommitReusesOriginalSnapshot() {
        long id = account("SAVINGS", "10000", "0");
        sql.execute("ALTER TABLE dbo.EOD_JOB_RUN ADD CONSTRAINT injected_failure CHECK (status <> 'SUCCESS')");
        assertThatThrownBy(() -> service(END, END).accrue(END)).isInstanceOf(RuntimeException.class);
        assertThat(audit.completed(END)).isTrue();
        sql.execute("ALTER TABLE dbo.EOD_JOB_RUN DROP CONSTRAINT injected_failure");
        sql.update("UPDATE dbo.ACCOUNT SET current_balance = 20000 WHERE account_id = ?", id);
        assertThat(service(END.plusDays(1), END).accrue(END).replayed()).isTrue();
        service(END.plusDays(1), END).postMonth(END);
        assertThat(balance(id)).isEqualByComparingTo("20001.10");
    }

    @Test void outboxFailureRollsBackBalanceLedgerGlAndJobThenRetryPostsOnce() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END, END);
        worker.accrue(END);
        sql.execute("ALTER TABLE dbo.OUTBOX_EVENT ADD CONSTRAINT injected_failure CHECK (event_type <> 'TRANSACTION_SUCCESS')");
        assertThatThrownBy(() -> worker.postMonth(END)).isInstanceOf(RuntimeException.class);
        assertThat(balance(id)).isEqualByComparingTo("10000");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.GL_ENTRY", Integer.class)).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.LEDGER_TRANSACTION", Integer.class)).isZero();
        assertThat(ledger.postingComplete(END)).isFalse();
        assertThat(audit.completed(END)).isTrue();
        sql.execute("ALTER TABLE dbo.OUTBOX_EVENT DROP CONSTRAINT injected_failure");
        worker.postMonth(END);
        assertThat(balance(id)).isEqualByComparingTo("10001.10");
    }

    @Test void concurrentPostingCreditsOnce() throws Exception {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END, END);
        worker.accrue(END);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Callable<InterestEodService.Result> task = () -> { go.await(); return worker.postMonth(END); };
            var first = executor.submit(task);
            var second = executor.submit(task);
            go.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).accounts() + second.get(30, TimeUnit.SECONDS).accounts()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
        assertThat(balance(id)).isEqualByComparingTo("10001.10");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.OUTBOX_EVENT", Integer.class)).isEqualTo(1);
    }

    @Test void missingDayPreventsPostingAndCannotBeBackfilledFromLiveBalance() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END, END.minusDays(1));
        worker.accrue(END);
        assertThatThrownBy(() -> worker.postMonth(END)).hasMessageContaining("missing 2026-10-30");
        assertThatThrownBy(() -> worker.accrue(END.minusDays(1))).hasMessageContaining("Historical EOD snapshot");
        assertThat(balance(id)).isEqualByComparingTo("10000");
    }

    @Test void zeroInterestSealsGlPeriodWithoutZeroLedgerTransaction() {
        account("SAVINGS", "0", "0");
        var worker = service(END, END);
        worker.runToday();
        assertThat(sql.queryForObject("SELECT amount FROM dbo.GL_ENTRY", BigDecimal.class)).isEqualByComparingTo("0");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.LEDGER_TRANSACTION", Integer.class)).isZero();
        assertThat(worker.postMonth(END).replayed()).isTrue();
    }

    @Test void recoveryPostsClosedMonthAfterRestart() {
        long id = account("SAVINGS", "10000", "0");
        service(END, END).accrue(END);
        service(END.plusDays(1), END).recoverClosedMonths();
        service(END.plusDays(1), END).recoverClosedMonths();
        assertThat(balance(id)).isEqualByComparingTo("10001.10");
    }

    @Test void leapFebruaryUses365AndRealCalendarMonthEnd() {
        long id = account("SAVINGS", "10000", "0");
        LocalDate leapEnd = LocalDate.of(2028, 2, 29);
        service(leapEnd, leapEnd).runToday();
        assertThat(balance(id)).isEqualByComparingTo("10001.10");
        assertThat(pg.queryForObject("SELECT interest_amount FROM interest_accrual", BigDecimal.class))
                .isEqualTo(new BigDecimal("1.095890"));
    }
}
