package com.bank.auth.banking;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final ExternalSettlementClient settlement;
    private final TransactionTemplate transaction;
    private final boolean isSqlServer;
    public ExternalTransferService(BankingService banking, JdbcTemplate jdbc, ExternalSettlementClient settlement,
                                   PlatformTransactionManager transactionManager) {
        this.banking=banking; this.jdbc=jdbc; this.settlement=settlement;
        this.transaction=new TransactionTemplate(transactionManager);
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
    public Receipt transfer(String authorization, Request request) {
        long customer = banking.authenticatedCustomer(authorization).getCustomerId();
        String reference=transaction.execute(status -> enqueue(customer,request));
        if ("INSTAPAY".equals(request.rail())) settle(reference,customer);
        return receipts(customer,reference).get(0);
    }

    private String enqueue(long customer, Request request) {
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
            return reference;
        }
        if (!source.status().equals("ACTIVE") || !source.currency().equals("PHP")) throw error(HttpStatus.BAD_REQUEST,"Choose an active PHP account.");
        if (source.availableBalance().compareTo(request.amount())<0) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Not enough available balance.");
        String type="EXT_"+request.rail()+"_"+recipient.id();
        // Persist intent before contacting the orchestrator. It alone screens/holds/posts funds.
        jdbc.update("INSERT INTO LEDGER_TRANSACTION (from_account_id,amount,source_currency,target_currency,transaction_type,reference_no,status) VALUES (?,?,'PHP','PHP',?,?,'PENDING')",
            source.id(),request.amount(),type,reference);
        return reference;
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
    public void settleBatch() {
        var cutoff=Timestamp.valueOf(LocalDateTime.now().minusSeconds(90));
        // No auth-service transaction surrounds HTTP. Core obtains the balance/hold locks.
        var pending=jdbc.query("""
                SELECT t.reference_no,a.customer_id FROM LEDGER_TRANSACTION t
                JOIN ACCOUNT a ON a.account_id=t.from_account_id
                WHERE t.status='PENDING' AND (t.transaction_type LIKE 'EXT_INSTAPAY_%'
                  OR (t.transaction_type LIKE 'EXT_PESONET_%' AND t.transaction_date<=?))
                ORDER BY t.transaction_date,t.transaction_id OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY
                """, (rs,n)->Map.entry(rs.getString(1),rs.getLong(2)),cutoff);
        for (var entry:pending) settle(entry.getKey(),entry.getValue());
    }
    private void settle(String reference,long customer) {
        try { settlement.settle(reference,customer); }
        catch (org.springframework.web.client.RestClientException error) {
            // Unknown outcomes remain durable and are retried with exactly the same reference.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("External settlement pending ref={}: {}",reference,error.getClass().getSimpleName());
        }
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
