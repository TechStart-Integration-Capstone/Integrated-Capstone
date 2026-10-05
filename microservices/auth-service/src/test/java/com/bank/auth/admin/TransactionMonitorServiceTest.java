package com.bank.auth.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionMonitorServiceTest {
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE ALIAS ISJSON FOR 'com.bank.auth.admin.TransactionMonitorServiceTest.isJson'");
        jdbc.execute("CREATE ALIAS JSON_VALUE FOR 'com.bank.auth.admin.TransactionMonitorServiceTest.jsonValue'");
        jdbc.execute("CREATE TABLE dbo.ACCOUNT(account_id BIGINT PRIMARY KEY, account_number VARCHAR(30), account_type VARCHAR(30))");
        jdbc.execute("CREATE TABLE dbo.LEDGER_TRANSACTION(transaction_id BIGINT PRIMARY KEY, reference_no VARCHAR(64), transaction_date TIMESTAMP, from_account_id BIGINT, to_account_id BIGINT, transaction_type VARCHAR(30), operation VARCHAR(10), amount DECIMAL(18,4), source_currency VARCHAR(10), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE dbo.OUTBOX_EVENT(event_id BIGINT PRIMARY KEY, transaction_id BIGINT, payload VARCHAR(4000))");
        jdbc.update("INSERT INTO dbo.ACCOUNT VALUES (10, '001181233469', 'SAVINGS_ACCOUNT')");
        jdbc.update("INSERT INTO dbo.ACCOUNT VALUES (99, 'PH1000000LOAN', 'INTERNAL')");
    }

    public static int isJson(String value) {
        try { return new ObjectMapper().readTree(value).isContainerNode() ? 1 : 0; }
        catch (Exception ex) { return 0; }
    }

    public static String jsonValue(String value, String path) throws Exception {
        return new ObjectMapper().readTree(value).path(path.substring(2)).asText(null);
    }

    private TransactionMonitorService monitor(String instant) {
        return new TransactionMonitorService(jdbc, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void insert(long id, String date, String type) {
        insert(id, date, type, 10L, null);
    }

    private void insert(long id, String date, String type, long fromAccountId, Long toAccountId) {
        jdbc.update("INSERT INTO dbo.LEDGER_TRANSACTION (transaction_id, reference_no, transaction_date, from_account_id, to_account_id, transaction_type, amount, source_currency, status) VALUES (?, ?, ?, ?, ?, ?, 15.2500, 'PHP', 'SUCCESS')",
                id, "DB-REF-" + id, LocalDateTime.parse(date), fromAccountId, toAccountId, type);
    }

    private void insert(long id, String date, String type, String operation) {
        insert(id, date, type, 10L, null);
        if (operation != null) {
            jdbc.update("INSERT INTO dbo.OUTBOX_EVENT VALUES (?, ?, ?)", id + 100, id,
                    "{\"operation\":\"" + operation + "\"}");
        }
    }

    @Test void filtersPhilippineDayAndOrdersNewestFirstIncludingTies() {
        insert(1, "2026-10-02T15:59:59.999999", "CREDIT");
        insert(2, "2026-10-02T16:00:00", "WELCOME_GIFT");
        insert(3, "2026-10-03T10:00:00", "TRANSFER_OUT");
        insert(4, "2026-10-03T16:00:00", "CREDIT");
        insert(5, "2026-10-03T10:00:00", "TRANSFER_IN");
        var rows = monitor("2026-10-03T12:00:00Z").today();
        assertThat(rows).extracting(TransactionMonitorService.Row::transactionId).containsExactly("5", "3", "2");
        assertThat(rows.get(2).transactionDate()).isEqualTo(OffsetDateTime.parse("2026-10-02T16:00:00Z"));
        assertThat(rows.get(0).referenceNo()).isEqualTo("DB-REF-5");
        assertThat(rows.get(0).accountNumber()).isEqualTo("001181233469");
    }

    @Test void switchesDayAtPhilippineMidnight() {
        insert(1, "2026-10-03T15:59:59", "CREDIT");
        insert(2, "2026-10-03T16:00:00", "CREDIT");
        assertThat(monitor("2026-10-03T15:59:59Z").today()).extracting(TransactionMonitorService.Row::transactionId).containsExactly("1");
        assertThat(monitor("2026-10-03T16:00:00Z").today()).extracting(TransactionMonitorService.Row::transactionId).containsExactly("2");
    }

    @Test void readsLoanAndOutboxOperationsWithoutDuplicatingTransactionsOrGuessingUnknownOperations() {
        insert(1, "2026-10-03T10:00:00", "TRANSFER_INSTAPAY");
        insert(2, "2026-10-03T10:00:00", "LOAN_DISBURSEMENT", 99, 10L); // bank → borrower
        insert(3, "2026-10-03T10:00:00", "OTHER");
        insert(4, "2026-10-03T10:00:00", "EXT_PESONET_BDO");
        jdbc.update("INSERT INTO dbo.OUTBOX_EVENT VALUES (1, 1, ?), (2, 1, ?), (3, 3, ?)",
                "{\"operation\":\"DEBIT\"}", "{\"operation\":\"DEBIT\"}", "invalid json");
        var rows = monitor("2026-10-03T12:00:00Z").today();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(TransactionMonitorService.Row::operation).containsExactly("DEBIT", null, "CREDIT", "DEBIT");
        // The disbursement is shown on the borrower's account, not the bank's loan pool.
        assertThat(rows.get(2).accountNumber()).isEqualTo("001181233469");
    }

    @Test void loanRepaymentIsADebitOnTheBorrowersAccount() {
        insert(1, "2026-10-03T10:00:00", "LOAN_REPAYMENT", 10, 99L);
        var rows = monitor("2026-10-03T12:00:00Z").today();
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.operation()).isEqualTo("DEBIT");
            assertThat(r.accountNumber()).isEqualTo("001181233469");
        });
    }

    @Test void emptyDatabaseReturnsNoSampleRows() {
        assertThat(monitor("2026-10-03T12:00:00Z").today()).isEmpty();
    }

    @Test void excludesTransactionsInvolvingDedicatedTestAccounts() {
        jdbc.update("INSERT INTO dbo.ACCOUNT VALUES (20, '001981233461', 'STRESS_TEST_ACCOUNT')");
        insert(1, "2026-10-03T10:00:00", "DEBIT", null);
        insert(2, "2026-10-03T11:00:00", "TRANSFER_OUT", null);
        insert(3, "2026-10-03T12:00:00", "CREDIT", null);
        jdbc.update("UPDATE dbo.LEDGER_TRANSACTION SET from_account_id = 20 WHERE transaction_id = 1");
        jdbc.update("UPDATE dbo.LEDGER_TRANSACTION SET to_account_id = 20 WHERE transaction_id = 2");
        assertThat(monitor("2026-10-03T12:00:00Z").today())
                .extracting(TransactionMonitorService.Row::transactionId).containsExactly("3");
    }
}
