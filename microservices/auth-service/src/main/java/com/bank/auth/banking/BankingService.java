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
                Integer count = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM ACCOUNT WHERE SUBSTR(account_number,5,7)=?) "
                        + "+ (SELECT COUNT(*) FROM AUDIT_LOG WHERE action='ACCOUNT_RENUMBERED' AND SUBSTR(details,5,7)=?) FROM DUAL",
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
        return jdbc.query("SELECT t.transaction_id, a.account_id, a.account_number, t.amount, t.source_currency, "
                        + "t.transaction_type, COALESCE((SELECT JSON_VALUE(o.payload, '$.operation') FROM OUTBOX_EVENT o "
                        + "WHERE o.transaction_id = t.transaction_id ORDER BY o.event_id FETCH FIRST 1 ROW ONLY), "
                        + "CASE WHEN t.transaction_type IN ('DEBIT','CREDIT') THEN t.transaction_type END), "
                        + "t.reference_no, t.status, t.transaction_date, c.first_name || ' ' || c.last_name, target.account_number FROM TRANSACTION t "
                        + "JOIN ACCOUNT a ON a.account_id = t.from_account_id "
                        + "LEFT JOIN ACCOUNT target ON target.account_id = t.to_account_id AND t.transaction_type IN ('TRANSFER_OUT','TRANSFER_IN') "
                        + "LEFT JOIN CUSTOMER c ON c.customer_id = target.customer_id WHERE a.customer_id = ? "
                        + "ORDER BY t.transaction_date DESC, t.transaction_id DESC FETCH FIRST 200 ROWS ONLY",
                (rs, row) -> new Activity(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), BankingIdentifiers.reference(rs.getLong(1),rs.getTimestamp(10).toLocalDateTime()), rs.getString(9),
                        rs.getTimestamp(10).toLocalDateTime(), ExternalTransferService.recipientForType(rs.getString(6)) == null ? rs.getString(11) : ExternalTransferService.recipientForType(rs.getString(6)).name(), ExternalTransferService.recipientForType(rs.getString(6)) == null ? rs.getString(12) : ExternalTransferService.recipientForType(rs.getString(6)).number()), customer.getCustomerId());
    }
}
