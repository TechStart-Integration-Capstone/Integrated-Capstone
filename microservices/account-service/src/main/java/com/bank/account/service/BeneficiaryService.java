package com.bank.account.service;

import com.bank.account.dto.RecipientDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * PayPink 2.0 — Beneficiary & Recipient Directory Service (Phase 5).
 * Consolidated into account-service.
 */
@Service
public class BeneficiaryService {

    private final JdbcTemplate jdbc;
    private static final String NAME = "c.first_name + ' ' + c.last_name";

    public BeneficiaryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public RecipientDto lookup(Long customerId, String number) {
        String normalized = normalize(number);
        List<RecipientDto> matches = jdbc.query(
                "SELECT a.account_number, " + NAME + ", "
                + "(SELECT COUNT(*) FROM dbo.BANKING_FAVORITE f WHERE f.customer_id = ? AND f.account_id = a.account_id) "
                + "FROM dbo.ACCOUNT a JOIN dbo.CUSTOMER c ON c.customer_id = a.customer_id "
                + "WHERE a.account_number = ? AND a.status = 'ACTIVE' AND c.status = 'ACTIVE' AND a.currency = 'PHP'",
                (rs, row) -> new RecipientDto(rs.getString(1), rs.getString(2), rs.getInt(3) > 0),
                customerId != null ? customerId : 0L, normalized);

        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active PayPink account matches this number.");
        }
        return matches.get(0);
    }

    @Transactional(readOnly = true)
    public RecipientDto.DirectoryDto directory(Long customerId) {
        if (customerId == null) return new RecipientDto.DirectoryDto(List.of(), List.of());

        List<RecipientDto> favorites = jdbc.query(
                "SELECT a.account_number, " + NAME
                + " FROM dbo.BANKING_FAVORITE f JOIN dbo.ACCOUNT a ON a.account_id = f.account_id JOIN dbo.CUSTOMER c ON c.customer_id = a.customer_id "
                + "WHERE f.customer_id = ? AND a.status = 'ACTIVE' AND c.status = 'ACTIVE' AND a.currency = 'PHP' ORDER BY f.created_date DESC, a.account_id",
                (rs, row) -> new RecipientDto(rs.getString(1), rs.getString(2), true), customerId);

        List<RecipientDto> recent = jdbc.query(
                "SELECT a.account_number, " + NAME + ", "
                + "(SELECT COUNT(*) FROM dbo.BANKING_FAVORITE f WHERE f.customer_id = ? AND f.account_id = a.account_id) "
                + "FROM dbo.ACCOUNT a JOIN dbo.CUSTOMER c ON c.customer_id = a.customer_id JOIN "
                + "(SELECT t.to_account_id, MAX(t.transaction_date) AS last_used FROM dbo.LEDGER_TRANSACTION t "
                + "JOIN dbo.ACCOUNT source ON source.account_id = t.from_account_id WHERE source.customer_id = ? "
                + "AND t.transaction_type IN ('TRANSFER_OUT','TRANSFER_IN','P2P_REMITTANCE') AND t.status = 'SUCCESS' GROUP BY t.to_account_id) r "
                + "ON r.to_account_id = a.account_id WHERE a.customer_id <> ? AND a.status = 'ACTIVE' AND c.status = 'ACTIVE' AND a.currency = 'PHP' "
                + "ORDER BY r.last_used DESC, a.account_id OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY",
                (rs, row) -> new RecipientDto(rs.getString(1), rs.getString(2), rs.getInt(3) > 0),
                customerId, customerId, customerId);

        return new RecipientDto.DirectoryDto(favorites, recent);
    }

    @Transactional
    public RecipientDto saveFavorite(Long customerId, String number) {
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing customer identity.");
        }
        RecipientDto recipient = lookup(customerId, number);

        // Serialize repeat saves idempotently
        jdbc.queryForObject("SELECT customer_id FROM dbo.CUSTOMER WITH (UPDLOCK, ROWLOCK) WHERE customer_id = ?", Long.class, customerId);
        jdbc.update("INSERT INTO dbo.BANKING_FAVORITE (customer_id, account_id) SELECT ?, a.account_id FROM dbo.ACCOUNT a "
                + "WHERE a.account_number = ? AND NOT EXISTS (SELECT 1 FROM dbo.BANKING_FAVORITE f WHERE f.customer_id = ? AND f.account_id = a.account_id)",
                customerId, recipient.accountNumber(), customerId);

        return new RecipientDto(recipient.accountNumber(), recipient.fullName(), true);
    }

    @Transactional
    public void removeFavorite(Long customerId, String number) {
        if (customerId == null) return;
        jdbc.update("DELETE FROM dbo.BANKING_FAVORITE WHERE customer_id = ? AND account_id IN (SELECT account_id FROM dbo.ACCOUNT WHERE account_number = ?)",
                customerId, normalize(number));
    }

    private String normalize(String number) {
        if (number == null || !number.strip().matches("[A-Za-z0-9-]{3,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the full PayPink account number.");
        }
        return number.replaceAll("\\s", "");
    }
}
