package com.bank.reconciliation.service;

import com.bank.reconciliation.model.oracle.TransactionRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Replays one evidenced audit leg only. Never changes balances or outbox delivery status. */
@Service
public class OutboxAuditRecovery {
    private static final Logger log = LoggerFactory.getLogger(OutboxAuditRecovery.class);
    private final JdbcTemplate ledger;
    private final JdbcTemplate audit;
    private final ObjectMapper mapper;

    @Autowired
    public OutboxAuditRecovery(@Qualifier("azureSqlDataSource") DataSource ledger,
                               @Qualifier("postgresDataSource") DataSource audit, ObjectMapper mapper) {
        this(new JdbcTemplate(ledger), new JdbcTemplate(audit), mapper);
    }

    OutboxAuditRecovery(JdbcTemplate ledger, JdbcTemplate audit, ObjectMapper mapper) {
        this.ledger = ledger;
        this.audit = audit;
        this.mapper = mapper;
    }

    record Event(long id, String type, String payload, long accountId, String currency, String transactionType) {}
    record Leg(long accountId, String operation, String currency, BigDecimal amount,
               BigDecimal before, BigDecimal after) {}

    public boolean recover(TransactionRecord transaction) {
        if (!"SUCCESS".equalsIgnoreCase(transaction.getStatus())) return false;
        List<Event> events = ledger.query("""
                SELECT o.event_id, o.event_type, o.payload, t.from_account_id, t.source_currency, t.transaction_type
                FROM app.OUTBOX_EVENT o JOIN t24.LEDGER_TRANSACTION t ON t.transaction_id=o.transaction_id
                WHERE t.transaction_id=? AND t.status='SUCCESS'
                    AND o.event_type IN ('TRANSACTION_SUCCESS', 'REMITTANCE_COMPLETED')
                ORDER BY o.event_id
                """, (rs, n) -> new Event(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getString(5), rs.getString(6)), transaction.getTransactionId());
        if (events.isEmpty()) return false;
        Leg expected = null;
        try {
            for (Event event : events) {
                Leg candidate = validate(event, transaction);
                if (expected != null && !expected.equals(candidate))
                    throw new IllegalArgumentException("Conflicting original events");
                expected = candidate;
            }
        } catch (Exception invalidEvidence) {
            log.warn("[reconciliation-service] txId={} has incomplete or conflicting outbox evidence; not recovered",
                    transaction.getTransactionId());
            return false;
        }
        // The unique transaction/account key arbitrates concurrent consumer/recovery inserts.
        // Existing audit rows are never overwritten, including rows with a different amount.
        int inserted = audit.update("""
                INSERT INTO LEDGER_MUTATION_AUDIT
                    (transaction_id, account_id, entry_type, amount, currency, before_balance, after_balance)
                SELECT ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM LEDGER_MUTATION_AUDIT WHERE transaction_id=?)
                ON CONFLICT (transaction_id, account_id) DO NOTHING
                """, transaction.getTransactionId(), expected.accountId(), expected.operation(), expected.amount(),
                expected.currency(), expected.before(), expected.after(), transaction.getTransactionId());
        if (inserted > 0) log.info("[reconciliation-service] Recovered txId={} from original outbox eventId={}",
                transaction.getTransactionId(), events.get(0).id());
        return inserted > 0;
    }

    private Leg validate(Event event, TransactionRecord transaction) throws Exception {
        JsonNode node = mapper.readTree(event.payload());
        long transactionId = decimal(node, "transactionId").longValueExact();
        long accountId = decimal(node, "accountId").longValueExact();
        String operation = text(node, "operation");
        String currency = text(node, "currency");
        BigDecimal amount = decimal(node, "amount");
        BigDecimal before = decimal(node, "beforeBalance");
        BigDecimal after = decimal(node, "afterBalance");
        if (transactionId != transaction.getTransactionId() || accountId <= 0 || accountId != event.accountId()
                || !text(node, "referenceNo").equals(transaction.getReferenceNo())
                || !currency.equals(event.currency()) || currency.length() > 10
                || amount.signum() <= 0 || amount.compareTo(transaction.getAmount()) != 0
                || before.signum() < 0 || after.signum() < 0
                || !(operation.equals("DEBIT") || operation.equals("CREDIT"))
                || (event.type().equals("REMITTANCE_COMPLETED") && !operation.equals("DEBIT"))
                || (event.transactionType().equals("INTEREST_CREDIT") && !operation.equals("CREDIT"))
                || (node.has("transactionType") && !text(node, "transactionType").equals(event.transactionType()))
                || (node.has("status") && !List.of("SUCCESS", "POSTED").contains(text(node, "status"))))
            throw new IllegalArgumentException("Outbox event does not match the successful ledger row");
        BigDecimal calculated = operation.equals("DEBIT") ? before.subtract(amount) : before.add(amount);
        if (calculated.compareTo(after) != 0) throw new IllegalArgumentException("Invalid balance delta");
        return new Leg(accountId, operation, currency, amount, before, after);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IllegalArgumentException("Missing " + field);
        return value.textValue();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !(value.isNumber() || value.isTextual()))
            throw new IllegalArgumentException("Missing " + field);
        BigDecimal number = new BigDecimal(value.asText()).setScale(4, RoundingMode.UNNECESSARY);
        if (number.precision() > 18) throw new IllegalArgumentException("Invalid precision");
        return number;
    }
}
