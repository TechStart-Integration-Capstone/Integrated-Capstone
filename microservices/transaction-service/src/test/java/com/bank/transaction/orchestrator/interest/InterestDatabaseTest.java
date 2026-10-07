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
    private String sqlMigration;
    private String recoveryMigration;
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
        sqlMigration = Files.readString(root.resolve("scripts/migrate_interest_azuresql.sql"));
        sql.execute(sqlMigration); // migration is rerunnable
        sql.update("UPDATE dbo.ACCOUNT SET status = 'INACTIVE'");
        pg.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        String migration = Files.readString(root.resolve("scripts/migrate_interest_postgres.sql"));
        recoveryMigration = Files.readString(root.resolve("scripts/migrate_interest_recovery_postgres.sql"));
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
                INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, current_balance, interest_rate, created_date)
                OUTPUT INSERTED.account_id VALUES (1, ?, ?, ?, ?, '2020-01-01T00:00:00')
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
        assertThat(sql.queryForObject("SELECT OBJECT_ID('dbo.GL_ENTRY', 'U')", Integer.class)).isNull();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.LEDGER_TRANSACTION", Integer.class)).isEqualTo(1);
        String payload = sql.queryForObject("SELECT payload FROM dbo.OUTBOX_EVENT", String.class);
        var event = new ObjectMapper().readTree(payload);
        assertThat(event.path("operation").asText()).isEqualTo("CREDIT");
        assertThat(event.path("beforeBalance").asText()).isEqualTo("10000.0000");
        assertThat(event.path("afterBalance").asText()).isEqualTo("10033.9700");
        assertThat(event.path("transactionType").asText()).isEqualTo("INTEREST_CREDIT");
        assertThat(event.path("periodEnd").asText()).isEqualTo("2026-10-31");
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

    @Test void outboxFailureRollsBackBalanceLedgerAndJobThenRetryPostsOnce() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END, END);
        worker.accrue(END);
        sql.execute("ALTER TABLE dbo.OUTBOX_EVENT ADD CONSTRAINT injected_failure CHECK (event_type <> 'TRANSACTION_SUCCESS')");
        assertThatThrownBy(() -> worker.postMonth(END)).isInstanceOf(RuntimeException.class);
        assertThat(balance(id)).isEqualByComparingTo("10000");
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

    @Test void zeroInterestCompletesJobWithoutFinancialEntry() {
        account("SAVINGS", "0", "0");
        var worker = service(END, END);
        worker.runBusinessDate(END);
        assertThat(ledger.postingComplete(END)).isTrue();
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

    @Test void replayChecksExistingTransactionAgainstImmutableAccrualAmount() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END, END);
        worker.runBusinessDate(END);
        sql.update("UPDATE dbo.LEDGER_TRANSACTION SET amount = 2 WHERE reference_no = ?", "INT-" + END + "-" + id);
        assertThatThrownBy(() -> worker.postMonth(END)).hasMessageContaining("differs from immutable accruals");
        assertThat(balance(id)).isEqualByComparingTo("10001.10");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.OUTBOX_EVENT", Integer.class)).isEqualTo(1);
    }

    @Test void retirementMigrationRefusesToDiscardHistoricalGlEntries() {
        sql.execute("CREATE TABLE dbo.GL_ENTRY (legacy_id INT NOT NULL)");
        sql.update("INSERT INTO dbo.GL_ENTRY VALUES (1)");
        assertThatThrownBy(() -> sql.execute(sqlMigration)).hasMessageContaining("historical entries");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.GL_ENTRY", Integer.class)).isEqualTo(1);
    }

    @Test void leapFebruaryUses365AndRealCalendarMonthEnd() {
        long id = account("SAVINGS", "10000", "0");
        LocalDate leapEnd = LocalDate.of(2028, 2, 29);
        service(leapEnd, leapEnd).runBusinessDate(leapEnd);
        assertThat(balance(id)).isEqualByComparingTo("10001.10");
        assertThat(pg.queryForObject("SELECT interest_amount FROM interest_accrual", BigDecimal.class))
                .isEqualTo(new BigDecimal("1.095890"));
    }
    private InterestRecoveryRequest backfill(long id, String balance) {
        return new InterestRecoveryRequest(InterestRecoveryRequest.Mode.BACKFILL,
                "Recovered after outage; complete eligible account manifest", "EOD-export-2026-10-30", true,
                List.of(new InterestRecoveryRequest.HistoricalAccount(id, "SAVINGS", new BigDecimal(balance), null)));
    }

    @Test void recoveryMigrationUpgradesExistingImmutableBatchWithoutChangingSnapshot() {
        account("SAVINGS", "10000", "0");
        service(END, END).accrue(END);
        pg.execute("""
                ALTER TABLE interest_accrual_batch DROP CONSTRAINT ck_interest_resolution,
                    DROP COLUMN resolution_type, DROP COLUMN resolved_by, DROP COLUMN resolution_reason,
                    DROP COLUMN source_reference, DROP COLUMN request_hash
                """);
        pg.execute(recoveryMigration);
        pg.execute(recoveryMigration);
        assertThat(audit.resolution(END).mode()).isEqualTo("SNAPSHOT");
        assertThat(audit.resolution(END).actor()).isNull();
        assertThat(pg.queryForObject("SELECT interest_amount FROM interest_accrual", BigDecimal.class))
                .isEqualByComparingTo("1.095890");
        assertThat(pg.update("UPDATE interest_accrual_batch SET resolution_type = 'WAIVER'")).isZero();
    }

    @Test void recoveryCannotReopenAnAlreadyPostedPeriod() {
        long id = account("SAVINGS", "10000", "0");
        service(END, END).runBusinessDate(END);
        assertThatThrownBy(() -> service(END.plusDays(1), END.withDayOfMonth(1))
                .prepareBackfill(END.minusDays(1), backfill(id, "1000"), "aly"))
                .hasMessageContaining("already posted");
        assertThat(audit.completed(END.minusDays(1))).isFalse();
    }

    @Test void missingDayBackfillUnblocksPostingWithoutUsingCurrentBalance() {
        long id = account("SAVINGS", "10000", "0");
        LocalDate missing = END.minusDays(1);
        var worker = service(END.plusDays(1), missing);
        service(END, missing).accrue(END);
        assertThat(worker.missingDays(END)).containsExactly(missing);
        assertThat(worker.recoverClosedMonths().blockedPeriods()).containsKey(END);
        var proposal = worker.prepareBackfill(missing, backfill(id, "1000"), "aly");
        assertThat(proposal.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(audit.completed(missing)).isFalse();
        assertThat(worker.missingDays(END)).containsExactly(missing);
        assertThatThrownBy(() -> worker.postMonth(END)).hasMessageContaining("missing");
        worker.approveBackfill(proposal.proposalId(), new InterestRecoveryRequest.Approval("Verified source and completeness", true), "checker");
        assertThat(balance(id)).isEqualByComparingTo("10000");
        assertThat(audit.resolution(missing).actor()).isEqualTo("aly");
        assertThat(audit.resolution(missing).mode()).isEqualTo("BACKFILL");
        assertThat(worker.prepareBackfill(missing, backfill(id, "1000.0000"), "another-admin").proposalId()).isEqualTo(proposal.proposalId());
        assertThat(worker.approveBackfill(proposal.proposalId(), new InterestRecoveryRequest.Approval("Verified source and completeness", true), "checker").replayed()).isTrue();
        assertThat(worker.backfillProposal(proposal.proposalId()).approvedBy()).isEqualTo("checker");
        assertThatThrownBy(() -> worker.prepareBackfill(missing, backfill(id, "2000"), "aly")).hasMessageContaining("already sealed");
        assertThat(worker.recoverClosedMonths().blockedPeriods()).isEmpty();
        assertThat(balance(id)).isEqualByComparingTo("10001.16"); // .068493 + 1.095890
        worker.recoverClosedMonths();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM dbo.LEDGER_TRANSACTION", Integer.class)).isEqualTo(1);
    }

    @Test void waiverAndUnapprovedBackfillAreRejectedByDatabaseAndDayStaysUnresolved() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END.plusDays(1), END.minusDays(1));
        service(END, END.minusDays(1)).accrue(END);
        for (String mode : List.of("WAIVER", "BACKFILL")) {
            assertThatThrownBy(() -> audit.append(END.minusDays(1), List.of(),
                    new InterestAccrualStore.Resolution(mode, "aly", "reason", "source", "a".repeat(64))))
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(worker.recoverClosedMonths().blockedPeriods()).containsKey(END);
        assertThat(balance(id)).isEqualByComparingTo("10000");
        assertThat(worker.missingDays(END)).containsExactly(END.minusDays(1));
    }

    @Test void sameAdminMustFileThenApproveAndBothAuditRecordsCannotBeChanged() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END.plusDays(1), END);
        var proposal = worker.prepareBackfill(END, backfill(id, "1000"), " Aly ");
        var approval = new InterestRecoveryRequest.Approval("Checked historical source", true);
        assertThat(audit.completed(END)).isFalse();
        assertThat(worker.overview(END).postingStatus()).isEqualTo("BLOCKED");
        assertThat(worker.overview(END).proposals().get(0).status()).isEqualTo("PENDING_APPROVAL");
        assertThatThrownBy(() -> worker.approveBackfill(proposal.proposalId(),
                new InterestRecoveryRequest.Approval("Not yet checked", false), "ALY"))
                .hasMessageContaining("Review confirmation");
        worker.approveBackfill(proposal.proposalId(), approval, "ALY");
        assertThat(worker.overview(END).postingStatus()).isEqualTo("READY");
        assertThat(worker.overview(END).missingDays()).isEmpty();
        assertThat(worker.overview(END).proposals().get(0).status()).isEqualTo("APPROVED");
        var approved = worker.backfillProposal(proposal.proposalId());
        assertThat(approved.approvedBy()).isEqualTo("aly");
        assertThat(approved.preparedAt()).isNotNull();
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.approvalReason()).isEqualTo("Checked historical source");
        assertThat(pg.update("UPDATE interest_backfill_proposal SET prepared_by = 'checker'")).isZero();
        assertThat(pg.update("DELETE FROM interest_backfill_proposal")).isZero();
        assertThat(pg.update("UPDATE interest_backfill_approval SET approval_reason = 'changed'")).isZero();
        assertThat(pg.update("DELETE FROM interest_backfill_approval")).isZero();
        assertThatThrownBy(() -> pg.execute("TRUNCATE interest_backfill_approval")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> pg.execute("TRUNCATE interest_backfill_proposal CASCADE")).hasMessageContaining("append-only");
        assertThat(worker.backfillProposal(proposal.proposalId()).preparedBy()).isEqualTo("aly");
        worker.postMonth(END);
        assertThat(worker.overview(END).postingStatus()).isEqualTo("POSTED");
    }

    @Test void overviewUsesConfiguredStartAndDoesNotTreatTodayAsMissing() {
        var worker = service(END.minusDays(3), END.minusDays(4));
        assertThat(worker.overview(null).periodStart()).isEqualTo(END.minusDays(4));
        assertThat(worker.overview(null).missingDays()).containsExactly(END.minusDays(4));
        service(END.minusDays(4), END.minusDays(4)).accrue(END.minusDays(4));
        assertThat(worker.overview(null).postingStatus()).isEqualTo("ACCRUING");
    }

    @Test void failedBackfillRollsBackApprovalAndAllAccrualsTogether() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END.plusDays(1), END);
        var proposal = worker.prepareBackfill(END, backfill(id, "1000"), "aly");
        pg.execute("ALTER TABLE interest_accrual ADD CONSTRAINT injected_failure CHECK (account_id <> " + id + ")");
        var approval = new InterestRecoveryRequest.Approval("Checked source", true);
        assertThatThrownBy(() -> worker.approveBackfill(proposal.proposalId(), approval, "checker"))
                .isInstanceOf(RuntimeException.class);
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_backfill_approval", Integer.class)).isZero();
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_accrual", Integer.class)).isZero();
        assertThat(audit.completed(END)).isFalse();
        assertThat(worker.backfillProposal(proposal.proposalId()).getStatus()).isEqualTo("PENDING_APPROVAL");
        pg.execute("ALTER TABLE interest_accrual DROP CONSTRAINT injected_failure");
        worker.approveBackfill(proposal.proposalId(), approval, "checker");
        assertThat(audit.completed(END)).isTrue();
    }

    @Test void concurrentApprovalCompletesBackfillExactlyOnce() throws Exception {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END.plusDays(1), END);
        var proposal = worker.prepareBackfill(END, backfill(id, "1000"), "aly");
        var approval = new InterestRecoveryRequest.Approval("Checked source", true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Callable<InterestEodService.Result> task = () -> {
                go.await();
                return worker.approveBackfill(proposal.proposalId(), approval, "checker");
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            go.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).accounts() + second.get(30, TimeUnit.SECONDS).accounts()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_backfill_approval", Integer.class)).isEqualTo(1);
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_accrual", Integer.class)).isEqualTo(1);
    }

    @Test void historicalRecoveryCannotIncludeAccountsOpenedAfterTheMissingDate() {
        long id = account("SAVINGS", "10000", "0");
        sql.update("UPDATE dbo.ACCOUNT SET created_date = '2026-10-31T00:00:00' WHERE account_id = ?", id);
        assertThatThrownBy(() -> service(END, END.minusDays(1)).prepareBackfill(END.minusDays(1), backfill(id, "10000"), "aly"))
                .hasMessageContaining("does not exist on that date");
        assertThat(audit.completed(END.minusDays(1))).isFalse();
    }

    @Test void recoveryCommitSurvivesAzureFailureAndCanBeReplayed() {
        long id = account("SAVINGS", "10000", "0");
        var worker = service(END, END.minusDays(1));
        var proposal = worker.prepareBackfill(END.minusDays(1), backfill(id, "1000"), "aly");
        sql.execute("ALTER TABLE dbo.EOD_JOB_RUN ADD CONSTRAINT injected_failure CHECK (status <> 'SUCCESS')");
        assertThatThrownBy(() -> worker.approveBackfill(proposal.proposalId(), new InterestRecoveryRequest.Approval("verified", true), "checker"))
                .isInstanceOf(RuntimeException.class);
        assertThat(audit.completed(END.minusDays(1))).isTrue();
        sql.execute("ALTER TABLE dbo.EOD_JOB_RUN DROP CONSTRAINT injected_failure");
        assertThat(worker.approveBackfill(proposal.proposalId(), new InterestRecoveryRequest.Approval("verified", true), "checker").replayed()).isTrue();
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_accrual", Integer.class)).isEqualTo(1);
    }

    @Test void september20AccountEarnsOnlySeptember20Through30() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        long id = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfMonth() == 20) {
                id = account("SAVINGS_ACCOUNT", "10000", "0");
                sql.update("UPDATE dbo.ACCOUNT SET created_date = '2026-09-20T00:00:00' WHERE account_id = ?", id);
            }
            service(date, start).runBusinessDate(date);
        }
        assertThat(balance(id)).isEqualByComparingTo("10012.05");
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_accrual WHERE account_id = ?", Integer.class, id)).isEqualTo(11);
        assertThat(pg.queryForObject("SELECT COUNT(*) FROM interest_accrual_batch", Integer.class)).isEqualTo(30);
    }

    @Test void unfinishedEarlierMonthDoesNotPreventRecoveryOfCompleteLaterMonth() {
        long id = account("SAVINGS", "10000", "0");
        LocalDate start = END.minusMonths(1); // September 30 is missing
        for (LocalDate date = END.withDayOfMonth(1); !date.isAfter(END); date = date.plusDays(1))
            service(date, start).accrue(date);
        var result = service(END.plusDays(1), start).recoverClosedMonths();
        assertThat(result.blockedPeriods()).containsOnlyKeys(start);
        assertThat(balance(id)).isEqualByComparingTo("10033.97");
    }
}
