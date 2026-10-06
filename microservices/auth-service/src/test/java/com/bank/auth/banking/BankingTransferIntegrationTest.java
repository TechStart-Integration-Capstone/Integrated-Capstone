package com.bank.auth.banking;

import com.bank.auth.model.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(BankingTransferIntegrationTest.Config.class)
class BankingTransferIntegrationTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:bankingtransfers;MODE=MSSQLServer;NON_KEYWORDS=TRANSACTION;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            return source;
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BankingService banking() { return mock(BankingService.class); }
        @Bean ExternalTransferService external(BankingService banking, JdbcTemplate jdbc, BankingLedger ledger) { return new ExternalTransferService(banking,jdbc,ledger); }
        @Bean BankingLedger ledger(JdbcTemplate jdbc) { return new BankingLedger(jdbc, new ObjectMapper()); }
        @Bean BankingRecipientService recipients(BankingService banking, JdbcTemplate jdbc) { return new BankingRecipientService(banking,jdbc); }
        @Bean BankingTransferService transfers(BankingService banking, JdbcTemplate jdbc, BankingLedger ledger) {
            return new BankingTransferService(banking, jdbc, ledger);
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired BankingService banking;
    @Autowired BankingTransferService transfers;
    @Autowired BankingRecipientService recipients;
    @Autowired ExternalTransferService external;

    @Test void accountNumberMigrationPreservesAccountsBalancesAndFavorites() {
        jdbc.update("INSERT INTO BANKING_FAVORITE(customer_id,account_id) VALUES(42,3)");
        jdbc.update("INSERT INTO AUDIT_LOG(customer_id,action,entity,details) VALUES(99,'ACCOUNT_RENUMBERED','ACCOUNT:3','PP-RECIPIENT')");
        jdbc.update("UPDATE ACCOUNT SET account_number='100000000003' WHERE account_id=3");
        var receipt=transfers.transfer("owner",request(1,"100000000003","1","number_format_test_01"));
        var migration=new BankingAccountNumberMigration(jdbc);
        migration.run(null);
        var numbers=jdbc.queryForList("SELECT account_number FROM ACCOUNT ORDER BY account_id",String.class);
        int audits=count("AUDIT_LOG");
        migration.run(null);
        assertThat(jdbc.queryForList("SELECT account_number FROM ACCOUNT ORDER BY account_id",String.class)).isEqualTo(numbers);
        assertThat(count("AUDIT_LOG")).isEqualTo(audits);
        assertThat(numbers).allMatch(BankingIdentifiers::isAccount).doesNotHaveDuplicates();
        assertThat(numbers.get(0)).startsWith("0012");
        assertThat(numbers.get(1)).startsWith("0011");
        assertThat(numbers.get(0).substring(4,11)).isEqualTo(numbers.get(1).substring(4,11));
        assertThat(numbers.get(2).substring(4,11)).isNotEqualTo(numbers.get(0).substring(4,11));
        assertThat(balance(1)).isEqualByComparingTo("49");
        assertThat(count("ACCOUNT")).isEqualTo(5);
        assertThat(count("BANKING_FAVORITE")).isEqualTo(1);
        assertThat(receipt.reference()).matches("PP-[0-9]{8}-[0-9]{12}");
        assertThat(transfers.transfer("owner",request(1,numbers.get(2),"1","number_format_test_01")).reference()).isEqualTo(receipt.reference());
        assertThat(transfers.transfer("owner",request(1,"100000000003","1","number_format_test_01")).reference()).isEqualTo(receipt.reference());
        assertThat(transfers.transfer("owner",request(1,"PP-RECIPIENT","1","number_format_test_01")).reference()).isEqualTo(receipt.reference());
        assertThat(recipients.lookup("owner","100000000003").accountNumber()).isEqualTo(numbers.get(2));
        assertThat(balance(1)).isEqualByComparingTo("49");
    }

    @Test void migrationPreservesAnAlreadyStructuredCustomerNumber() {
        String existing=BankingIdentifiers.account("EVERYDAY_ACCOUNT","0000123");
        jdbc.update("UPDATE ACCOUNT SET account_number=? WHERE account_id=1",existing);
        new BankingAccountNumberMigration(jdbc).run(null);
        assertThat(jdbc.queryForObject("SELECT account_number FROM ACCOUNT WHERE account_id=1",String.class)).isEqualTo(existing);
        assertThat(jdbc.queryForObject("SELECT account_number FROM ACCOUNT WHERE account_id=2",String.class))
                .isEqualTo(BankingIdentifiers.account("SAVINGS_ACCOUNT","0000123"));
    }

    @Test void duplicateAccountTypesFailBeforeChangingAnyNumbers() {
        jdbc.update("UPDATE ACCOUNT SET account_type='EVERYDAY_ACCOUNT' WHERE account_id=2");
        assertThatThrownBy(()->new BankingAccountNumberMigration(jdbc).run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT account_number FROM ACCOUNT WHERE account_id=1",String.class)).isEqualTo("PP-EVERYDAY");
        assertThat(count("AUDIT_LOG")).isZero();
    }

    @Test void pesonetWaitsBeforeDebitingAndPostsOnlyOnce() {
        var request=new ExternalTransferService.Request(1L,"001234567890",new BigDecimal("20"),"PESONET","delayed_payment_001");
        assertThat(external.transfer("owner",request).status()).isEqualTo("PENDING");
        assertThat(external.transfer("owner",request).status()).isEqualTo("PENDING");
        external.settleBatch();
        assertThat(balance(1)).isEqualByComparingTo("50");
        assertThat(count("OUTBOX_EVENT")).isZero();
        assertThat(count("AUDIT_LOG")).isZero();
        jdbc.update("UPDATE LEDGER_TRANSACTION SET transaction_date=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(91)));
        external.settleBatch(); external.settleBatch();
        assertThat(balance(1)).isEqualByComparingTo("30");
        assertThat(external.history("owner").get(0).status()).isEqualTo("COMPLETED");
        assertThat(count("LEDGER_TRANSACTION")).isEqualTo(1);
        assertThat(count("OUTBOX_EVENT")).isEqualTo(1);
        assertThat(count("AUDIT_LOG")).isEqualTo(1);
    }

    @Test void competingPendingPaymentsCannotOverdraw() {
        external.transfer("owner",new ExternalTransferService.Request(1L,"001234567890",new BigDecimal("40"),"PESONET","delayed_payment_001"));
        external.transfer("owner",new ExternalTransferService.Request(1L,"009876543210",new BigDecimal("40"),"PESONET","delayed_payment_002"));
        jdbc.update("UPDATE LEDGER_TRANSACTION SET transaction_date=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(91)));
        external.settleBatch();
        assertThat(balance(1)).isEqualByComparingTo("10");
        assertThat(external.history("owner")).extracting(ExternalTransferService.Receipt::status).containsExactlyInAnyOrder("COMPLETED","FAILED");
        assertThat(count("OUTBOX_EVENT")).isEqualTo(1);
    }

    @BeforeEach void prepare() {
        reset(banking);
        Customer owner = mock(Customer.class); when(owner.getCustomerId()).thenReturn(42L);
        Customer recipient = mock(Customer.class); when(recipient.getCustomerId()).thenReturn(99L);
        when(banking.authenticatedCustomer("owner")).thenReturn(owner);
        when(banking.authenticatedCustomer("recipient")).thenReturn(recipient);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE CUSTOMER (customer_id BIGINT PRIMARY KEY, status VARCHAR(20), first_name VARCHAR(100), last_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE ACCOUNT (account_id BIGINT PRIMARY KEY, customer_id BIGINT, account_number VARCHAR(30) UNIQUE, "
                + "currency VARCHAR(10), current_balance DECIMAL(18,4) CHECK(current_balance >= 0), held_balance DECIMAL(18,4) DEFAULT 0, status VARCHAR(20), account_type VARCHAR(30))");
        jdbc.execute("CREATE TABLE LEDGER_TRANSACTION (transaction_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                + "from_account_id BIGINT, to_account_id BIGINT, amount DECIMAL(18,4) CHECK(amount > 0), source_currency VARCHAR(10), "
                + "target_currency VARCHAR(10), transaction_type VARCHAR(30), reference_no VARCHAR(64) UNIQUE, status VARCHAR(20), "
                + "transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE OUTBOX_EVENT (event_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, transaction_id BIGINT, "
                + "event_type VARCHAR(50), payload CLOB, status VARCHAR(20))");
        jdbc.execute("CREATE TABLE AUDIT_LOG (customer_id BIGINT, action VARCHAR(100), entity VARCHAR(50), details VARCHAR(4000))");
        jdbc.execute("CREATE TABLE BANKING_FAVORITE (customer_id BIGINT, account_id BIGINT, created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(customer_id,account_id))");
        jdbc.update("INSERT INTO CUSTOMER VALUES (42, 'ACTIVE', 'Jamie', 'Rivera'), (99, 'ACTIVE', 'Alex', 'Cruz')");
        jdbc.update("INSERT INTO ACCOUNT VALUES (1,42,'PP-EVERYDAY','PHP',50,0,'ACTIVE','EVERYDAY_ACCOUNT'), (2,42,'PP-SAVINGS','PHP',0,0,'ACTIVE','SAVINGS_ACCOUNT'), "
                + "(3,99,'PP-RECIPIENT','PHP',50,0,'ACTIVE','EVERYDAY_ACCOUNT'), (4,99,'PP-USD','USD',0,0,'ACTIVE','SAVINGS_ACCOUNT'), (5,99,'PP-CLOSED','PHP',0,0,'CLOSED','CHECKING_ACCOUNT')");
    }
    private BankingTransferService.Request request(long source, String destination, String amount, String key) {
        return new BankingTransferService.Request(source,destination,new BigDecimal(amount),key);
    }
    private BigDecimal balance(long id) { return jdbc.queryForObject("SELECT current_balance FROM ACCOUNT WHERE account_id = ?",BigDecimal.class,id); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Integer.class); }

    @Test void transferDebitsAndCreditsWithTwoAuditAndOutboxEntries() {
        var receipt = transfers.transfer("owner",request(1,"PP-SAVINGS","20.25","own_transfer_key_01"));
        assertThat(balance(1)).isEqualByComparingTo("29.75");
        assertThat(balance(2)).isEqualByComparingTo("20.25");
        assertThat(receipt.status()).isEqualTo("SUCCESS");
        assertThat(receipt.recipientName()).isEqualTo("Jamie Rivera");
        assertThat(count("LEDGER_TRANSACTION")).isEqualTo(2);
        assertThat(count("AUDIT_LOG")).isEqualTo(2);
        assertThat(count("OUTBOX_EVENT")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT status FROM OUTBOX_EVENT",String.class)).containsOnly("PENDING");
    }

    @Test void sequentialAndConcurrentRetriesDoNotDoubleDebit() throws Exception {
        var request = request(1,"PP-RECIPIENT","40","duplicate_request_01");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var results = pool.invokeAll(List.<Callable<BankingTransferService.Receipt>>of(
                    () -> transfers.transfer("owner",request), () -> transfers.transfer("owner",request)));
            assertThat(results.get(0).get(10,TimeUnit.SECONDS)).isEqualTo(results.get(1).get(10,TimeUnit.SECONDS));
            assertThat(transfers.transfer("owner",request)).isEqualTo(results.get(0).get());
            assertThat(balance(1)).isEqualByComparingTo("10");
            assertThat(balance(3)).isEqualByComparingTo("90");
            assertThat(count("LEDGER_TRANSACTION")).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }

    @Test void competingTransfersCannotOverspend() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            var results = pool.invokeAll(List.<Callable<Integer>>of(
                    () -> attempt(ready,request(1,"PP-SAVINGS","40","competing_request_01")),
                    () -> attempt(ready,request(1,"PP-RECIPIENT","40","competing_request_02"))));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,422);
            assertThat(balance(1)).isEqualByComparingTo("10");
            assertThat(balance(1).add(balance(2)).add(balance(3))).isEqualByComparingTo("100");
            assertThat(count("LEDGER_TRANSACTION")).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
    private int attempt(CountDownLatch ready, BankingTransferService.Request request) throws Exception {
        ready.countDown(); ready.await(5,TimeUnit.SECONDS);
        try { transfers.transfer("owner",request); return 200; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }

    @Test void oppositeDirectionTransfersUseConsistentLocks() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var results = pool.invokeAll(List.<Callable<BankingTransferService.Receipt>>of(
                    () -> transfers.transfer("owner",request(1,"PP-RECIPIENT","10","opposite_request_01")),
                    () -> transfers.transfer("recipient",request(3,"PP-EVERYDAY","10","opposite_request_02"))));
            for (var result : results) assertThat(result.get(10,TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            assertThat(balance(1)).isEqualByComparingTo("50");
            assertThat(balance(3)).isEqualByComparingTo("50");
        } finally { pool.shutdownNow(); }
    }

    @Test void failureWritingRecipientLegRollsBackBothBalancesAndAllRecords() {
        jdbc.execute("ALTER TABLE LEDGER_TRANSACTION ADD CONSTRAINT fail_recipient CHECK(transaction_type <> 'TRANSFER_IN')");
        assertThatThrownBy(() -> transfers.transfer("owner",request(1,"PP-SAVINGS","10","rollback_request_01")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(balance(1)).isEqualByComparingTo("50");
        assertThat(balance(2)).isEqualByComparingTo("0");
        assertThat(count("LEDGER_TRANSACTION")).isZero();
        assertThat(count("OUTBOX_EVENT")).isZero();
        assertThat(count("AUDIT_LOG")).isZero();
    }

    @Test void rejectsSomeoneElsesSourceSelfTransfersInvalidRecipientsAndInsufficientFunds() {
        assertRejected(403,request(3,"PP-SAVINGS","1","unauthorized_key_01"));
        assertRejected(400,request(1,"PP-EVERYDAY","1","same_account_key_01"));
        assertRejected(404,request(1,"PP-MISSING","1","missing_account_01"));
        assertRejected(400,request(1,"PP-USD","1","currency_error_01"));
        assertRejected(409,request(1,"PP-CLOSED","1","closed_account_01"));
        assertRejected(422,request(1,"PP-SAVINGS","50.01","insufficient_key_01"));
        assertThat(count("LEDGER_TRANSACTION")).isZero();
        assertThat(balance(1)).isEqualByComparingTo("50");
    }

    @Test void reusingKeyForDifferentDetailsIsRejected() {
        transfers.transfer("owner",request(1,"PP-SAVINGS","10","reused_request_01"));
        assertRejected(409,request(1,"PP-SAVINGS","20","reused_request_01"));
        assertThat(balance(1)).isEqualByComparingTo("40");
        assertThat(count("LEDGER_TRANSACTION")).isEqualTo(2);
    }
    private void assertRejected(int status, BankingTransferService.Request request) {
        assertThatThrownBy(() -> transfers.transfer("owner",request)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(status));
    }

    @Test void exactLookupReturnsNameWithoutExposingBalancesAndRejectsUnavailableAccounts() {
        assertThat(recipients.lookup("owner","pp-recipient").fullName()).isEqualTo("Alex Cruz");
        for (String number : new String[]{"PP-REC", "PP-CLOSED", "PP-USD"})
            assertThatThrownBy(() -> recipients.lookup("owner",number)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> recipients.lookup("owner","%" )).isInstanceOf(ResponseStatusException.class);
    }

    @Test void favoritesAreDurableIdempotentAndScopedToCustomer() {
        recipients.save("owner","PP-RECIPIENT");
        recipients.save("owner","PP-RECIPIENT");
        assertThat(count("BANKING_FAVORITE")).isEqualTo(1);
        assertThat(recipients.directory("owner").favorites()).hasSize(1);
        assertThat(recipients.directory("recipient").favorites()).isEmpty();
        recipients.remove("recipient","PP-RECIPIENT");
        assertThat(recipients.lookup("owner","PP-RECIPIENT").favorite()).isTrue();
        recipients.remove("owner","PP-RECIPIENT");
        assertThat(recipients.directory("owner").favorites()).isEmpty();
    }

    @Test void recentRecipientsIncludeTransfersPostedByTheRemittanceOrchestrator() {
        // The 30-second-hold transfer flow (transaction-service) records sends as P2P_REMITTANCE.
        jdbc.update("INSERT INTO LEDGER_TRANSACTION(from_account_id,to_account_id,amount,source_currency,target_currency,transaction_type,reference_no,status) "
                + "VALUES(1,3,10,'PHP','PHP','P2P_REMITTANCE','TX-PH-RECENT01','SUCCESS')");
        assertThat(recipients.directory("owner").recent()).singleElement()
                .satisfies(r -> assertThat(r.fullName()).isEqualTo("Alex Cruz"));
    }

    @Test void recentRecipientsIncludeSentAndReceivedTransfersWithoutDuplicates() {
        transfers.transfer("owner",request(1,"PP-RECIPIENT","10","recipient_history_01"));
        transfers.transfer("owner",request(1,"PP-RECIPIENT","5","recipient_history_02"));
        assertThat(recipients.directory("owner").recent()).hasSize(1);
        assertThat(recipients.directory("owner").recent().get(0).fullName()).isEqualTo("Alex Cruz");
        assertThat(recipients.directory("recipient").recent()).hasSize(1);
        assertThat(recipients.directory("recipient").recent().get(0).fullName()).isEqualTo("Jamie Rivera");
    }
}
