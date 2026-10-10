package com.bank.auth.banking;

import com.bank.auth.model.Customer;
import com.bank.auth.repository.CustomerRepository;
import com.bank.auth.security.JwtTokenProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Transfers posted by transaction-service are one P2P_REMITTANCE row; both the sender and the receiver must see it. */
class BankingActivityQueryTest {

    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE ALIAS JSON_VALUE FOR 'com.bank.auth.admin.TransactionMonitorServiceTest.jsonValue'");
        jdbc.execute("CREATE TABLE CUSTOMER (customer_id BIGINT PRIMARY KEY, first_name VARCHAR(100), last_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE ACCOUNT (account_id BIGINT PRIMARY KEY, customer_id BIGINT, account_number VARCHAR(30), account_type VARCHAR(30), "
                + "currency VARCHAR(10), current_balance DECIMAL(18,4), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE LEDGER_TRANSACTION (transaction_id BIGINT PRIMARY KEY, from_account_id BIGINT, to_account_id BIGINT, amount DECIMAL(18,4), "
                + "source_currency VARCHAR(10), target_currency VARCHAR(10), transaction_type VARCHAR(30), reference_no VARCHAR(64), status VARCHAR(20), "
                + "transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE OUTBOX_EVENT (event_id BIGINT PRIMARY KEY, transaction_id BIGINT, payload VARCHAR(4000))");
        jdbc.execute("CREATE SCHEMA app");
        jdbc.execute("CREATE TABLE app.REMITTANCE (remittance_id BIGINT PRIMARY KEY, reference_no VARCHAR(64), "
                + "caller_customer_id BIGINT, source_account_id BIGINT, target_account_id BIGINT, amount DECIMAL(18,4), "
                + "currency VARCHAR(10), transaction_type VARCHAR(30), status VARCHAR(30), created_at TIMESTAMP)");
        jdbc.update("INSERT INTO CUSTOMER VALUES (42,'Jamie','Rivera'), (99,'Alex','Cruz')");
        jdbc.update("INSERT INTO ACCOUNT VALUES (1,42,'001100000001','EVERYDAY_ACCOUNT','PHP',100,'ACTIVE'), (2,42,'001100000002','SAVINGS_ACCOUNT','PHP',0,'ACTIVE'), "
                + "(3,99,'001100000003','EVERYDAY_ACCOUNT','PHP',0,'ACTIVE')");
        // Jamie → Alex, and Jamie everyday → Jamie savings, both through transaction-service.
        jdbc.update("INSERT INTO LEDGER_TRANSACTION (transaction_id,from_account_id,to_account_id,amount,source_currency,target_currency,transaction_type,reference_no,status) "
                + "VALUES (10,1,3,25,'PHP','PHP','P2P_REMITTANCE','TX-PH-A','SUCCESS'), (11,1,2,5,'PHP','PHP','P2P_REMITTANCE','TX-PH-B','SUCCESS')");
        jdbc.update("INSERT INTO OUTBOX_EVENT VALUES (1,10,'{\"operation\":\"DEBIT\"}'), (2,11,'{\"operation\":\"DEBIT\"}')");
    }

    private BankingService serviceFor(long customerId) {
        Customer customer = mock(Customer.class);
        when(customer.getCustomerId()).thenReturn(customerId);
        BankingService service = spy(new BankingService(mock(CustomerRepository.class), jdbc, mock(PasswordEncoder.class),
                mock(JwtTokenProvider.class), mock(BankingLedger.class)));
        doReturn(customer).when(service).authenticatedCustomer(anyString());
        return service;
    }

    private void request(long id, String status) {
        jdbc.update("INSERT INTO app.REMITTANCE VALUES(?,?,42,1,3,999,'PHP','TRANSFER',?,CURRENT_TIMESTAMP)",
                id, "REQUEST-" + id, status);
    }

    private String report(long accountId) throws Exception {
        var banking = mock(BankingService.class);
        when(banking.profile(anyString())).thenReturn(new BankingService.Profile("Jamie", "Jamie Rivera", "jamie", "jamie@example.com",
                List.of(new BankingService.Account(accountId, "00110000000" + accountId, "EVERYDAY_ACCOUNT", "PHP", BigDecimal.ZERO, "ACTIVE"))));
        var today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"));
        return new String(new TransactionReportService(banking, jdbc).generate("token", accountId,
                today.minusDays(1), today), StandardCharsets.ISO_8859_1);
    }

    @ParameterizedTest
    @CsvSource({"Initiated,PENDING", "Authorized,PENDING", "Reserved,PENDING", "Processing,PENDING",
            "PROCESSING,PENDING", "T24_POSTED,PENDING", "Failed,FAILED", "REJECTED,FAILED",
            "Cancelled,CANCELLED", "REVERSED,CANCELLED"})
    void senderSeesUnpostedRequestsWithoutCountingThemAsCompleted(String raw, String expected) throws Exception {
        request(10, raw); // Deliberately collides with an existing ledger ID.
        assertThat(serviceFor(42).activity("token")).filteredOn(a -> a.reference().equals("REQUEST-10"))
                .singleElement().satisfies(a -> {
                    assertThat(a.transactionId()).isNull();
                    assertThat(a.status()).isEqualTo(expected);
                    assertThat(a.operation()).isEqualTo("DEBIT");
                    assertThat(a.type()).isEqualTo("P2P_REMITTANCE");
                    assertThat(a.counterpartyName()).isEqualTo("Alex Cruz");
                });
        assertThat(serviceFor(99).activity("token")).noneMatch(a -> a.reference().equals("REQUEST-10"));
        assertThat(report(1)).contains("REQUEST-10", "(" + expected + ")", "999.00",
                "Completed money out: PHP 30.00", "Completed money in: PHP 0.00");
        assertThat(report(3)).doesNotContain("REQUEST-10");
    }

    @Test void excludesOtherOwnersAndLoanOrExternalInstructions() throws Exception {
        for (int id = 20; id < 25; id++) request(id, "Failed");
        jdbc.update("UPDATE app.REMITTANCE SET caller_customer_id=99 WHERE remittance_id=20");
        jdbc.update("UPDATE app.REMITTANCE SET source_account_id=3 WHERE remittance_id=21");
        jdbc.update("UPDATE app.REMITTANCE SET transaction_type='LOAN_REPAYMENT' WHERE remittance_id=22");
        jdbc.update("UPDATE app.REMITTANCE SET transaction_type='LOAN_DISBURSEMENT' WHERE remittance_id=23");
        jdbc.update("UPDATE app.REMITTANCE SET transaction_type='EXT_INSTAPAY_BDO' WHERE remittance_id=24");
        assertThat(serviceFor(42).activity("token")).hasSize(3);
        assertThat(report(1)).doesNotContain("REQUEST-");
    }

    @Test void postingReplacesRequestWithoutDuplicateHistoryOrReportEntries() throws Exception {
        request(10, "Processing");
        assertThat(serviceFor(42).activity("token")).hasSize(4);
        jdbc.update("UPDATE LEDGER_TRANSACTION SET reference_no='REQUEST-10' WHERE transaction_id=10");
        // Ledger presence suppresses the request even if its status has not caught up yet.
        assertThat(serviceFor(42).activity("token")).hasSize(3).allMatch(a -> a.transactionId() != null);
        assertThat(report(1)).contains("(2 transactions)", "Completed money out: PHP 30.00").doesNotContain("999.00");
        jdbc.update("UPDATE app.REMITTANCE SET status='Posted'");
        assertThat(serviceFor(42).activity("token")).hasSize(3);
    }

    @Test void remittanceReferenceMatchesReceiptsForBothAccountsAndReports() throws Exception {
        assertThat(serviceFor(42).activity("token")).filteredOn(a -> Math.abs(a.transactionId()) == 11)
                .extracting(BankingService.Activity::reference).containsOnly("TX-PH-B");
        assertThat(serviceFor(99).activity("token")).singleElement()
                .satisfies(a -> assertThat(a.reference()).isEqualTo("TX-PH-A"));
        assertThat(report(1)).contains("TX-PH-A", "TX-PH-B");
        assertThat(report(2)).contains("TX-PH-B");
        assertThat(report(3)).contains("TX-PH-A");
    }

    @Test void requestReferenceSurvivesPostingIntoHistoryAndReport() throws Exception {
        request(10, "Processing");
        assertThat(serviceFor(42).activity("token")).anyMatch(a -> a.reference().equals("REQUEST-10"));
        jdbc.update("UPDATE LEDGER_TRANSACTION SET reference_no='REQUEST-10' WHERE transaction_id=10");
        jdbc.update("UPDATE app.REMITTANCE SET status='Posted'");
        assertThat(serviceFor(42).activity("token")).filteredOn(a -> a.reference().equals("REQUEST-10"))
                .singleElement().satisfies(a -> assertThat(a.transactionId()).isEqualTo(10));
        assertThat(report(1)).contains("REQUEST-10");
        assertThat(report(3)).contains("REQUEST-10");
    }

    @Test void legacyRowsRetainTheirDisplayReferencesAndMissingRemittanceReferenceFallsBack() throws Exception {
        jdbc.update("UPDATE LEDGER_TRANSACTION SET transaction_type='TRANSFER_OUT' WHERE transaction_id=10");
        jdbc.update("UPDATE LEDGER_TRANSACTION SET reference_no=NULL WHERE transaction_id=11");
        var rows = serviceFor(42).activity("token");
        assertThat(rows).allMatch(a -> a.reference().startsWith("PP-"));
        for (var row : rows) assertThat(report(row.accountId())).contains(row.reference());
        jdbc.update("UPDATE LEDGER_TRANSACTION SET reference_no='  ' WHERE transaction_id=11");
        assertThat(serviceFor(42).activity("token")).allMatch(a -> a.reference().startsWith("PP-"));
    }

    @Test void ownAccountRequestHasOnlySenderLegUntilPosted() throws Exception {
        request(15, "Processing");
        jdbc.update("UPDATE app.REMITTANCE SET target_account_id=2");
        assertThat(serviceFor(42).activity("token")).filteredOn(a -> a.reference().equals("REQUEST-15"))
                .singleElement().satisfies(a -> assertThat(a.accountId()).isEqualTo(1));
        assertThat(report(2)).doesNotContain("REQUEST-15");
    }

    @Test void reportRespectsPhilippineDateBoundariesForRequests() throws Exception {
        request(15, "Failed");
        request(16, "Failed");
        var today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"));
        var end = java.sql.Timestamp.from(today.plusDays(1).atStartOfDay(TransactionReportService.ZONE).toInstant());
        jdbc.update("UPDATE app.REMITTANCE SET created_at=? WHERE remittance_id=15", end);
        jdbc.update("UPDATE app.REMITTANCE SET created_at=? WHERE remittance_id=16",
                java.sql.Timestamp.from(end.toInstant().minusSeconds(1)));
        assertThat(report(1)).contains("REQUEST-16").doesNotContain("REQUEST-15");
    }

    @Test void latest200LimitAppliesAfterCombiningLedgerAndRequests() {
        for (int id = 100; id < 302; id++) request(id, "Processing");
        jdbc.update("UPDATE app.REMITTANCE SET created_at=DATEADD('DAY',1,CURRENT_TIMESTAMP)");
        var rows = serviceFor(42).activity("token");
        assertThat(rows).hasSize(200).allMatch(a -> a.transactionId() == null);
        assertThat(rows.get(0).reference()).isEqualTo("REQUEST-301");
        assertThat(rows.get(199).reference()).isEqualTo("REQUEST-102");
    }

    @Test void receiverSeesIncomingTransferAsCreditFromSender() {
        List<BankingService.Activity> activity = serviceFor(99).activity("token");

        assertThat(activity).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo("TRANSFER_IN");
            assertThat(a.operation()).isEqualTo("CREDIT");
            assertThat(a.accountNumber()).isEqualTo("001100000003");
            assertThat(a.amount()).isEqualByComparingTo("25");
            assertThat(a.counterpartyName()).isEqualTo("Jamie Rivera");
            assertThat(a.counterpartyAccountNumber()).isEqualTo("001100000001");
            assertThat(a.transactionId()).isEqualTo(-10L);
        });
    }

    @Test void senderSeesDebitsAndOwnAccountTransferShowsBothLegsWithDistinctIds() {
        List<BankingService.Activity> activity = serviceFor(42).activity("token");

        assertThat(activity).extracting(BankingService.Activity::transactionId).containsExactlyInAnyOrder(10L, 11L, -11L);
        assertThat(activity).filteredOn(a -> a.transactionId() == 10L).singleElement().satisfies(a -> {
            assertThat(a.operation()).isEqualTo("DEBIT");
            assertThat(a.counterpartyName()).isEqualTo("Alex Cruz");
        });
        assertThat(activity).filteredOn(a -> a.transactionId() == -11L).singleElement().satisfies(a -> {
            assertThat(a.operation()).isEqualTo("CREDIT");
            assertThat(a.accountNumber()).isEqualTo("001100000002");
        });
        // Both legs of one transfer share its customer-facing reference.
        assertThat(activity).filteredOn(a -> Math.abs(a.transactionId()) == 11L)
                .extracting(BankingService.Activity::reference).containsOnly(activity.stream()
                        .filter(a -> a.transactionId() == 11L).findFirst().orElseThrow().reference());
    }

    @Test void receiverStatementListsIncomingTransfer() throws Exception {
        BankingService banking = mock(BankingService.class);
        when(banking.profile(anyString())).thenReturn(new BankingService.Profile("Alex", "Alex Cruz", "alex", "alex@example.com",
                List.of(new BankingService.Account(3L, "001100000003", "EVERYDAY_ACCOUNT", "PHP", BigDecimal.ZERO, "ACTIVE"))));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"));

        byte[] pdf = new TransactionReportService(banking, jdbc).generate("token", 3L, today.minusDays(1), today);

        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("TRANSFER IN - Jamie Rivera");
    }
}
