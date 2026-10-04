package com.bank.auth.banking;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class BankingRecipientService {
    public record Recipient(String accountNumber, String fullName, boolean favorite) {}
    public record Directory(List<Recipient> favorites, List<Recipient> recent) {}
    private final BankingService banking;
    private final JdbcTemplate jdbc;
    public BankingRecipientService(BankingService banking, JdbcTemplate jdbc) { this.banking = banking; this.jdbc = jdbc; }
    private static final String NAME = "CONCAT(c.first_name, ' ', c.last_name)";

    @Transactional(readOnly = true)
    public Recipient lookup(String authorization, String number) {
        long customer = banking.authenticatedCustomer(authorization).getCustomerId();
        return find(customer, number);
    }
    private Recipient find(long customer, String number) {
        String normalized = normalize(number);
        List<Recipient> matches = jdbc.query("SELECT a.account_number, " + NAME + ", "
                + "(SELECT COUNT(*) FROM BANKING_FAVORITE f WHERE f.customer_id = ? AND f.account_id = a.account_id) "
                + "FROM ACCOUNT a JOIN CUSTOMER c ON c.customer_id = a.customer_id "
                + "WHERE a.account_number = ? AND a.status = 'ACTIVE' AND c.status = 'ACTIVE' AND a.currency = 'PHP'",
                (rs,row) -> new Recipient(rs.getString(1),rs.getString(2),rs.getInt(3) > 0), customer, normalized);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No active PayPink account matches this number.");
        return matches.get(0);
    }

    @Transactional(readOnly = true)
    public Directory directory(String authorization) {
        long customer = banking.authenticatedCustomer(authorization).getCustomerId();
        List<Recipient> favorites = jdbc.query("SELECT a.account_number, " + NAME
                + " FROM BANKING_FAVORITE f JOIN ACCOUNT a ON a.account_id = f.account_id JOIN CUSTOMER c ON c.customer_id = a.customer_id "
                + "WHERE f.customer_id = ? AND a.status = 'ACTIVE' AND c.status = 'ACTIVE' AND a.currency = 'PHP' ORDER BY f.created_date DESC, a.account_id",
                (rs,row) -> new Recipient(rs.getString(1),rs.getString(2),true),customer);
        List<Recipient> recent = jdbc.query("SELECT a.account_number, " + NAME + ", "
                + "(SELECT COUNT(*) FROM BANKING_FAVORITE f WHERE f.customer_id = ? AND f.account_id = a.account_id) "
                + "FROM ACCOUNT a JOIN CUSTOMER c ON c.customer_id = a.customer_id JOIN "
                + "(SELECT t.to_account_id, MAX(t.transaction_date) AS last_used FROM LEDGER_TRANSACTION t "
                + "JOIN ACCOUNT source ON source.account_id = t.from_account_id WHERE source.customer_id = ? "
                + "AND t.transaction_type IN ('TRANSFER_OUT','TRANSFER_IN') AND t.status = 'SUCCESS' GROUP BY t.to_account_id) r "
                + "ON r.to_account_id = a.account_id WHERE a.customer_id <> ? AND a.status = 'ACTIVE' AND c.status = 'ACTIVE' AND a.currency = 'PHP' "
                + "ORDER BY r.last_used DESC, a.account_id OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY",
                (rs,row) -> new Recipient(rs.getString(1),rs.getString(2),rs.getInt(3) > 0), customer,customer,customer);
        return new Directory(favorites,recent);
    }

    @Transactional
    public Recipient save(String authorization, String number) {
        long customer = banking.authenticatedCustomer(authorization).getCustomerId();
        Recipient recipient = find(customer,number);
        // Serialize repeated saves by this customer so adding a favorite is idempotent.
        jdbc.queryForObject("SELECT customer_id FROM CUSTOMER WHERE customer_id = ? FOR UPDATE",Long.class,customer);
        jdbc.update("INSERT INTO BANKING_FAVORITE (customer_id, account_id) SELECT ?, a.account_id FROM ACCOUNT a "
                + "WHERE a.account_number = ? AND NOT EXISTS (SELECT 1 FROM BANKING_FAVORITE f WHERE f.customer_id = ? AND f.account_id = a.account_id)",
                customer,recipient.accountNumber(),customer);
        return new Recipient(recipient.accountNumber(),recipient.fullName(),true);
    }

    @Transactional
    public void remove(String authorization, String number) {
        long customer = banking.authenticatedCustomer(authorization).getCustomerId();
        jdbc.update("DELETE FROM BANKING_FAVORITE WHERE customer_id = ? AND account_id IN (SELECT account_id FROM ACCOUNT WHERE account_number = ?)",
                customer,normalize(number));
    }
    private String normalize(String number) {
        if (number == null || !number.strip().matches("[A-Za-z0-9-]{3,30}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the full PayPink account number.");
        return BankingIdentifiers.resolveAccount(jdbc,number);
    }
}
