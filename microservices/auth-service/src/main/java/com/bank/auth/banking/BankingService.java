package com.bank.auth.banking;

import com.bank.auth.dto.AuthResponse;
import com.bank.auth.model.Customer;
import com.bank.auth.repository.CustomerRepository;
import com.bank.auth.security.JwtTokenProvider;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BankingService {
    public record Registration(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Pattern(regexp = "[+0-9() -]{7,30}") String phone,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}") String username,
            @NotBlank @Size(min = 8, max = 64) String password) {}
    public record Account(long accountId, String accountNumber, String accountType,
                          String currency, BigDecimal currentBalance, String status) {}
    public record Activity(long transactionId, long accountId, String accountNumber,
                           BigDecimal amount, String currency, String type, String operation,
                           String reference, String status, LocalDateTime date, String counterpartyName, String counterpartyAccountNumber) {}
    public record Profile(String firstName, String fullName, String username, String email,
                          List<Account> accounts) {}

    private final CustomerRepository customers;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtTokenProvider tokens;
    private final BankingLedger ledger;

    public BankingService(CustomerRepository customers, JdbcTemplate jdbc,
                          PasswordEncoder passwords, JwtTokenProvider tokens, BankingLedger ledger) {
        this.customers = customers; this.jdbc = jdbc; this.passwords = passwords; this.tokens = tokens;
        this.ledger = ledger;
    }

    @Transactional
    public AuthResponse register(Registration request) {
        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Integer matches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM CUSTOMER WHERE LOWER(username) = ? OR LOWER(email) = ?",
                Integer.class, username, email);
        if (matches != null && matches > 0) throw new ResponseStatusException(
                HttpStatus.CONFLICT, "That username or email is already registered.");
        Customer customer = customers.saveAndFlush(new Customer(username, passwords.encode(request.password()),
                request.firstName().strip(), request.lastName().strip(), email, request.phone().strip()));
        String customerNumber = createSavingsAccount(customer.getCustomerId());
        String number = createAccount(customer.getCustomerId(), "EVERYDAY_ACCOUNT", customerNumber, new BigDecimal("50.00"));
        Long accountId = jdbc.queryForObject("SELECT account_id FROM ACCOUNT WHERE account_number = ?", Long.class, number);
        // The welcome gift and both accounts commit together, with the credit visible in history and the outbox.
        ledger.record(new BankingLedger.Account(accountId, customer.getCustomerId(), number, "PHP", BigDecimal.ZERO, "ACTIVE"),
                null, new BigDecimal("50.00"), new BigDecimal("50.00"), "CREDIT", "WELCOME_GIFT", "WELCOME-" + customer.getCustomerId());
        List<String> roles = List.of("ROLE_CUSTOMER", "ROLE_RETAIL_USER");
        return new AuthResponse(tokens.generateToken(customer.getCustomerId(), username, roles), 86400000L,
                customer.getCustomerId(), username, customer.getFirstName() + " " + customer.getLastName(), roles);
    }

    private String createSavingsAccount(long customerId) {
        for(int attempt = 0; attempt < 128; attempt++) {
            String customerNumber = BankingIdentifiers.newCustomerNumber(candidate -> {
                Integer count = jdbc.queryForObject(
                        "SELECT (SELECT COUNT(*) FROM ACCOUNT WHERE SUBSTRING(account_number,5,7)=?) "
                        + "+ (SELECT COUNT(*) FROM AUDIT_LOG WHERE action='ACCOUNT_RENUMBERED' AND SUBSTRING(details,5,7)=?)",
                        Integer.class, candidate, candidate);
                return count != null && count > 0;
            });
            try {
                // The unique account-number constraint arbitrates concurrent registrations drawing the same number.
                createAccount(customerId, "SAVINGS_ACCOUNT", customerNumber, BigDecimal.ZERO);
                return customerNumber;
            } catch(DuplicateKeyException collision) { /* Retry with a new random customer number. */ }
        }
        throw new IllegalStateException("Unable to allocate a unique customer number");
    }

    private String createAccount(long customerId, String type, String customerNumber, BigDecimal balance) {
        String number = BankingIdentifiers.account(type, customerNumber);
        jdbc.update("INSERT INTO ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status) "
                + "VALUES (?, ?, ?, 'PHP', ?, 'ACTIVE')", customerId, number, type, balance);
        return number;
    }

    public Customer authenticatedCustomer(String authorization) {
        long id;
        try { id = tokens.customerId(authorization); }
        catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
        if (id == 0) {
            return new Customer(0L, "admin", "", "PayPink", "Administrator", "admin@paypink.ph", "+630000000000");
        }
        return customers.findById(id).filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again."));
    }

    @Transactional(readOnly = true)
    public Profile profile(String authorization) {
        Customer customer = authenticatedCustomer(authorization);
        List<Account> accounts = jdbc.query("SELECT account_id, account_number, account_type, currency, "
                        + "current_balance, status FROM ACCOUNT WHERE customer_id = ? ORDER BY account_id",
                (rs, row) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getString(6)), customer.getCustomerId());
        return new Profile(customer.getFirstName(), customer.getFirstName() + " " + customer.getLastName(),
                customer.getUsername(), customer.getEmail(), accounts);
    }

    @Transactional(readOnly = true)
    public List<Activity> activity(String authorization) {
        Customer customer = authenticatedCustomer(authorization);
        // The outbox records the actual debit/credit operation; transaction_type can be a payment rail.
        // Only the mutated account is selected: a target ID alone does not prove a recipient credit.
        // Exceptions: a LOAN_DISBURSEMENT is a single bank → borrower row, so the borrower's (target) account is the
        // one shown, as a CREDIT; a LOAN_REPAYMENT is the borrower's DEBIT.
        // A P2P_REMITTANCE (transaction-service transfer) is also a single row for both sides, so the second SELECT
        // adds the receiver's leg as a TRANSFER_IN credit with the sender as counterparty. That leg uses the negated
        // transaction_id as its key, so a transfer between the customer's own accounts lists two distinct entries.
        return jdbc.query("SELECT * FROM (SELECT t.transaction_id AS activity_id, t.transaction_id AS ledger_id, a.account_id, a.account_number, "
                        + "t.amount, t.source_currency, t.transaction_type, COALESCE(CASE t.transaction_type WHEN 'LOAN_DISBURSEMENT' THEN 'CREDIT' "
                        + "WHEN 'LOAN_REPAYMENT' THEN 'DEBIT' END, "
                        + "(SELECT TOP 1 JSON_VALUE(o.payload, '$.operation') FROM OUTBOX_EVENT o "
                        + "WHERE o.transaction_id = t.transaction_id ORDER BY o.event_id), "
                        + "CASE WHEN t.transaction_type IN ('DEBIT','CREDIT') THEN t.transaction_type END) AS operation, "
                        + "t.status, t.transaction_date, c.first_name + ' ' + c.last_name AS counterparty_name, "
                        + "target.account_number AS counterparty_account FROM LEDGER_TRANSACTION t "
                        + "JOIN ACCOUNT a ON a.account_id = CASE WHEN t.transaction_type = 'LOAN_DISBURSEMENT' "
                        + "THEN t.to_account_id ELSE t.from_account_id END "
                        + "LEFT JOIN ACCOUNT target ON target.account_id = t.to_account_id AND t.transaction_type IN ('TRANSFER_OUT','TRANSFER_IN','P2P_REMITTANCE') "
                        + "LEFT JOIN CUSTOMER c ON c.customer_id = target.customer_id WHERE a.customer_id = ? "
                        + "UNION ALL SELECT -t.transaction_id, t.transaction_id, a.account_id, a.account_number, t.amount, t.target_currency, "
                        + "'TRANSFER_IN', 'CREDIT', t.status, t.transaction_date, c.first_name + ' ' + c.last_name, source.account_number "
                        + "FROM LEDGER_TRANSACTION t JOIN ACCOUNT a ON a.account_id = t.to_account_id "
                        + "JOIN ACCOUNT source ON source.account_id = t.from_account_id "
                        + "LEFT JOIN CUSTOMER c ON c.customer_id = source.customer_id "
                        + "WHERE t.transaction_type = 'P2P_REMITTANCE' AND a.customer_id = ?) activity "
                        + "ORDER BY transaction_date DESC, ledger_id DESC, activity_id DESC OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY",
                (rs, row) -> {
                    String type = rs.getString("transaction_type");
                    var rail = ExternalTransferService.recipientForType(type);
                    LocalDateTime date = rs.getTimestamp("transaction_date").toLocalDateTime();
                    return new Activity(rs.getLong("activity_id"), rs.getLong("account_id"), rs.getString("account_number"),
                            rs.getBigDecimal("amount"), rs.getString("source_currency"), type, rs.getString("operation"),
                            BankingIdentifiers.reference(rs.getLong("ledger_id"), date), rs.getString("status"), date,
                            rail == null ? rs.getString("counterparty_name") : rail.name(),
                            rail == null ? rs.getString("counterparty_account") : rail.number());
                }, customer.getCustomerId(), customer.getCustomerId());
    }
}
