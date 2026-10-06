package com.bank.auth.banking;

import com.bank.auth.model.Customer;
import com.bank.auth.repository.CustomerRepository;
import com.bank.auth.security.JwtTokenProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
