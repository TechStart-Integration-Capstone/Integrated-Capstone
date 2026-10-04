package com.bank.auth.banking;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BankingTransferService {
    public record Request(@NotNull @Positive Long sourceAccountId,
                          @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{3,30}") String destinationAccountNumber,
                          @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,
                          @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,80}") String idempotencyKey) {}
    public record Receipt(String reference, long sourceAccountId, String destinationAccountNumber,
                          BigDecimal amount, String currency, String status, LocalDateTime date, String recipientName) {}
    private final BankingService banking;
    private final JdbcTemplate jdbc;
    private final BankingLedger ledger;
    public BankingTransferService(BankingService banking, JdbcTemplate jdbc, BankingLedger ledger) {
        this.banking = banking; this.jdbc = jdbc; this.ledger = ledger;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Receipt transfer(String authorization, Request request) {
        long customerId = banking.authenticatedCustomer(authorization).getCustomerId();
        String destination = BankingIdentifiers.resolveAccount(jdbc,request.destinationAccountNumber());
        String reference = reference(customerId, request.idempotencyKey());
        // A committed receipt is authoritative even if balances/statuses changed after the first request.
        Receipt replay = replay(reference, request, destination);
        if (replay != null) return replay;
        List<Long> targets = jdbc.query("SELECT account_id FROM ACCOUNT WHERE account_number = ?",
                (rs, row) -> rs.getLong(1), destination);
        if (targets.isEmpty()) throw error(HttpStatus.NOT_FOUND, "The recipient account could not be found. Check the account number.");
        long targetId = targets.get(0);
        if (targetId == request.sourceAccountId()) throw error(HttpStatus.BAD_REQUEST, "Choose a different receiving account.");
        // Ascending ID lock order prevents opposite-direction transfers from deadlocking.
        var first = lock(Math.min(request.sourceAccountId(), targetId));
        var second = lock(Math.max(request.sourceAccountId(), targetId));
        var source = first.id() == request.sourceAccountId() ? first : second;
        var target = first.id() == targetId ? first : second;
        if (source.customerId() != customerId) throw error(HttpStatus.FORBIDDEN, "You can only transfer from your own accounts.");
        // A concurrent retry may have committed while this request waited for the account locks.
        replay = replay(reference, request, destination);
        if (replay != null) return replay;
        if (!"ACTIVE".equals(source.status()) || !"ACTIVE".equals(target.status()))
            throw error(HttpStatus.CONFLICT, "Both accounts must be active to make a transfer.");
        Integer activeRecipient = jdbc.queryForObject("SELECT COUNT(*) FROM CUSTOMER WHERE customer_id = ? AND status = 'ACTIVE'",
                Integer.class, target.customerId());
        if (activeRecipient == null || activeRecipient == 0) throw error(HttpStatus.CONFLICT, "The recipient account is unavailable.");
        if (!"PHP".equals(source.currency()) || !source.currency().equals(target.currency()))
            throw error(HttpStatus.BAD_REQUEST, "Transfers are available between PHP accounts only.");
        if (source.availableBalance().compareTo(request.amount()) < 0)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough money in this account. Choose another account or a smaller amount.");
        BigDecimal sourceAfter = source.balance().subtract(request.amount());
        BigDecimal targetAfter = target.balance().add(request.amount());
        if (targetAfter.compareTo(new BigDecimal("99999999999999.9999")) > 0)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "The recipient balance limit would be exceeded.");
        jdbc.update("UPDATE ACCOUNT SET current_balance = ? WHERE account_id = ?", sourceAfter, source.id());
        jdbc.update("UPDATE ACCOUNT SET current_balance = ? WHERE account_id = ?", targetAfter, target.id());
        ledger.record(source, target.id(), request.amount(), sourceAfter, "DEBIT", "TRANSFER_OUT", reference + "-D");
        ledger.record(target, source.id(), request.amount(), targetAfter, "CREDIT", "TRANSFER_IN", reference + "-C");
        return Objects.requireNonNull(replay(reference, request, destination));
    }

    private BankingLedger.Account lock(long id) {
        List<BankingLedger.Account> accounts = jdbc.query("SELECT account_id, customer_id, account_number, currency, current_balance, COALESCE(held_balance, 0), status "
                        + "FROM ACCOUNT WHERE account_id = ? FOR UPDATE",
                (rs, row) -> new BankingLedger.Account(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7)), id);
        if (accounts.isEmpty()) throw error(HttpStatus.NOT_FOUND, "The account could not be found.");
        return accounts.get(0);
    }

    private Receipt replay(String reference, Request request, String destination) {
        List<Receipt> receipts = jdbc.query("SELECT t.from_account_id, a.account_number, t.amount, t.source_currency, t.transaction_date, CONCAT(c.first_name, ' ', c.last_name), t.transaction_id "
                        + "FROM LEDGER_TRANSACTION t JOIN ACCOUNT a ON a.account_id = t.to_account_id JOIN CUSTOMER c ON c.customer_id = a.customer_id WHERE t.reference_no = ? AND t.status = 'SUCCESS'",
                (rs, row) -> new Receipt(BankingIdentifiers.reference(rs.getLong(7),rs.getTimestamp(5).toLocalDateTime()), rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                        "SUCCESS", rs.getTimestamp(5).toLocalDateTime(), rs.getString(6)), reference + "-D");
        if (receipts.isEmpty()) return null;
        Receipt receipt = receipts.get(0);
        if (receipt.sourceAccountId() != request.sourceAccountId() || !receipt.destinationAccountNumber().equals(destination)
                || receipt.amount().compareTo(request.amount()) != 0)
            throw error(HttpStatus.CONFLICT, "This transfer request was already used for different details. Start a new transfer.");
        return receipt;
    }

    private String reference(long customerId, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((customerId + ":" + key).getBytes(StandardCharsets.UTF_8));
            return "TRF-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
