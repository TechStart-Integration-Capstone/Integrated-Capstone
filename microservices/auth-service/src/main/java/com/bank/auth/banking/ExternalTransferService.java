package com.bank.auth.banking;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;

@Service
public class ExternalTransferService {
    // Stable IDs are persisted in transaction_type; never reuse them for another recipient.
    public record Recipient(String id, String bank, String name, String number) {}
    public static final List<Recipient> RECIPIENTS = List.of(
        new Recipient("BDO", "BDO", "Alex Reyes", "001234567890"),
        new Recipient("BPI", "BPI", "Jamie Santos", "009876543210"),
        new Recipient("MB", "Metrobank", "Sam Rivera", "003456789012"));
    public record Request(@NotNull @Positive Long sourceAccountId, @NotBlank String destinationAccountNumber,
        @NotNull @DecimalMin("0.01") @Digits(integer=14,fraction=2) BigDecimal amount,
        @NotBlank @Pattern(regexp="INSTAPAY|PESONET") String rail,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,80}") String idempotencyKey) {}
    public record Receipt(String reference, long sourceAccountId, String destinationAccountNumber,
        BigDecimal amount, String currency, String status, LocalDateTime date,
        String recipientName, String bank, String rail, boolean mock) {}
    private final BankingService banking;
    private final JdbcTemplate jdbc;
    private final BankingLedger ledger;
    private final boolean isSqlServer;
    public ExternalTransferService(BankingService banking, JdbcTemplate jdbc, BankingLedger ledger) {
        this.banking=banking; this.jdbc=jdbc; this.ledger=ledger;
        boolean sqlServer = true;
        try (java.sql.Connection conn = jdbc.getDataSource() != null ? jdbc.getDataSource().getConnection() : null) {
            if (conn != null) {
                String product = conn.getMetaData().getDatabaseProductName();
                sqlServer = product != null && (product.contains("Microsoft") || product.contains("SQL Server"));
            }
        } catch (Exception e) { sqlServer = true; }
        this.isSqlServer = sqlServer;
    }
    public static Recipient recipientForType(String type) {
        if (type == null || !type.startsWith("EXT_")) return null;
        return RECIPIENTS.stream().filter(r -> type.endsWith("_"+r.id())).findFirst().orElse(null);
    }
    @Transactional
    public Receipt transfer(String authorization, Request request) {
        long customer = banking.authenticatedCustomer(authorization).getCustomerId();
        Recipient recipient = RECIPIENTS.stream().filter(r -> r.number().equals(request.destinationAccountNumber())).findFirst()
            .orElseThrow(() -> error(HttpStatus.BAD_REQUEST,"The recipient account could not be found. Check the account number."));
        if (!List.of("INSTAPAY","PESONET").contains(request.rail())) throw error(HttpStatus.BAD_REQUEST,"Choose a supported transfer method.");
        if (request.rail().equals("INSTAPAY") && request.amount().compareTo(new BigDecimal("50000")) > 0)
            throw error(HttpStatus.BAD_REQUEST,"InstaPay supports up to PHP 50,000 per transfer.");
        String reference;
        try { reference="EXT-"+Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest((customer+":"+request.idempotencyKey()).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        String lockSql = isSqlServer
            ? "SELECT account_id,customer_id,account_number,currency,current_balance,COALESCE(held_balance,0),status FROM ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_id=?"
            : "SELECT account_id,customer_id,account_number,currency,current_balance,COALESCE(held_balance,0),status FROM ACCOUNT WHERE account_id=? FOR UPDATE";
        var accounts=jdbc.query(lockSql,
            (rs,n)->new BankingLedger.Account(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getString(7)),request.sourceAccountId());
        if (accounts.isEmpty() || accounts.get(0).customerId()!=customer) throw error(HttpStatus.FORBIDDEN,"Choose one of your own accounts.");
        var source=accounts.get(0);
        var existing=receipts(customer,reference);
        if (!existing.isEmpty()) {
            var receipt=existing.get(0);
            if (receipt.sourceAccountId()!=request.sourceAccountId() || !receipt.destinationAccountNumber().equals(recipient.number())
                || !receipt.rail().equals(request.rail()) || receipt.amount().compareTo(request.amount())!=0)
                throw error(HttpStatus.CONFLICT,"This request was already used for different transfer details.");
            return receipt;
        }
        if (!source.status().equals("ACTIVE") || !source.currency().equals("PHP")) throw error(HttpStatus.BAD_REQUEST,"Choose an active PHP account.");
        if (source.availableBalance().compareTo(request.amount())<0) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Not enough available balance.");
        String type="EXT_"+request.rail()+"_"+recipient.id();
        if (request.rail().equals("PESONET")) {
            // Queue the instruction only. No balance mutation or successful debit event yet.
            jdbc.update("INSERT INTO LEDGER_TRANSACTION (from_account_id,amount,source_currency,target_currency,transaction_type,reference_no,status) VALUES (?,?,'PHP','PHP',?,?,'PENDING')",
                source.id(),request.amount(),type,reference);
        } else {
            var after=source.balance().subtract(request.amount());
            jdbc.update("UPDATE ACCOUNT SET current_balance=? WHERE account_id=?",after,source.id());
            ledger.record(source,null,request.amount(),after,"DEBIT",type,reference);
        }
        return receipts(customer,reference).get(0);
    }
    public List<Receipt> history(String authorization) {
        long customerId = banking.authenticatedCustomer(authorization).getCustomerId();
        return receipts(customerId, null);
    }
    private List<Receipt> receipts(long customer, String reference) {
        String sql = "SELECT t.reference_no,t.from_account_id,t.amount,t.status,t.transaction_date,t.transaction_type,t.transaction_id FROM LEDGER_TRANSACTION t JOIN ACCOUNT a ON a.account_id=t.from_account_id WHERE t.transaction_type LIKE 'EXT_%'";
        List<Object> params = new ArrayList<>();
        if (customer > 0) {
            sql += " AND a.customer_id=?";
            params.add(customer);
        }
        if (reference != null) {
            sql += " AND t.reference_no=?";
            params.add(reference);
        }
        sql += " ORDER BY t.transaction_date DESC OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY";
        return jdbc.query(sql, (rs, n) -> {
            String type = rs.getString(6); var recipient = Objects.requireNonNull(recipientForType(type));
            return new Receipt(BankingIdentifiers.reference(rs.getLong(7), rs.getTimestamp(5).toLocalDateTime()), rs.getLong(2), recipient.number(), rs.getBigDecimal(3), "PHP",
                "SUCCESS".equals(rs.getString(4)) ? "COMPLETED" : rs.getString(4), rs.getTimestamp(5).toLocalDateTime(),
                recipient.name(), recipient.bank(), type.contains("_PESONET_") ? "PESONET" : "INSTAPAY", true);
        }, params.toArray());
    }
    @Scheduled(fixedDelay=2000)
    @Transactional
    public void settleBatch() {
        var cutoff=Timestamp.valueOf(LocalDateTime.now().minusSeconds(90));
        // Lock accounts in ascending order, matching the internal-transfer lock order.
        var ids=jdbc.queryForList("SELECT DISTINCT from_account_id FROM LEDGER_TRANSACTION WHERE status='PENDING' AND transaction_type LIKE 'EXT_PESONET_%' AND transaction_date <= ? ORDER BY from_account_id",Long.class,cutoff);
        for (long id:ids) {
            String batchLockSql = isSqlServer
                ? "SELECT account_id,customer_id,account_number,currency,current_balance,COALESCE(held_balance,0),status FROM ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_id=?"
                : "SELECT account_id,customer_id,account_number,currency,current_balance,COALESCE(held_balance,0),status FROM ACCOUNT WHERE account_id=? FOR UPDATE";
            var source=jdbc.queryForObject(batchLockSql,
                (rs,n)->new BankingLedger.Account(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getString(7)),id);
            var pending=jdbc.queryForList("SELECT reference_no FROM LEDGER_TRANSACTION WHERE from_account_id=? AND status='PENDING' AND transaction_type LIKE 'EXT_PESONET_%' AND transaction_date <= ? ORDER BY transaction_date,transaction_id",String.class,id,cutoff);
            for (String reference:pending) {
                var tx=jdbc.queryForMap("SELECT transaction_id,amount,transaction_type FROM LEDGER_TRANSACTION WHERE reference_no=?",reference);
                BigDecimal amount=(BigDecimal)tx.get("AMOUNT");
                String type=(String)tx.get("TRANSACTION_TYPE");
                // Compatibility with previously submitted transfers whose debit was already posted.
                Integer posted=jdbc.queryForObject("SELECT COUNT(*) FROM OUTBOX_EVENT WHERE transaction_id=?",Integer.class,tx.get("TRANSACTION_ID"));
                if (posted != null && posted>0) {
                    jdbc.update("UPDATE LEDGER_TRANSACTION SET status='SUCCESS' WHERE reference_no=?",reference);
                    continue;
                }
                if (!"ACTIVE".equals(source.status()) || !"PHP".equals(source.currency()) || source.balance().compareTo(amount)<0) {
                    jdbc.update("UPDATE LEDGER_TRANSACTION SET status='FAILED' WHERE reference_no=?",reference);
                    jdbc.update("INSERT INTO AUDIT_LOG (customer_id,action,entity,details) VALUES (?,'PESONET_FAILED','ACCOUNT',?)",source.customerId(),"Account unavailable or insufficient funds at processing time; reference "+reference);
                    continue;
                }
                var after=source.balance().subtract(amount);
                jdbc.update("UPDATE ACCOUNT SET current_balance=? WHERE account_id=?",after,id);
                ledger.post(source,amount,after,"DEBIT",type,reference);
                jdbc.update("UPDATE LEDGER_TRANSACTION SET status='SUCCESS' WHERE reference_no=?",reference);
                source=new BankingLedger.Account(source.id(),source.customerId(),source.number(),source.currency(),after,source.status());
            }
        }
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
