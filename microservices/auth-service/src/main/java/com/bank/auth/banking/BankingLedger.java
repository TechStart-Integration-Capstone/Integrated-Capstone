package com.bank.auth.banking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Writes ledger, audit and outbox entries in the caller's Azure SQL transaction. */
@Component
public class BankingLedger {
    public record Account(long id, long customerId, String number, String currency, BigDecimal balance, String status) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public BankingLedger(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Account account, Long counterpartyId, BigDecimal amount, BigDecimal after,
                       String operation, String type, String reference) {
        jdbc.update("INSERT INTO LEDGER_TRANSACTION (from_account_id, to_account_id, amount, source_currency, target_currency, "
                        + "transaction_type, reference_no, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'SUCCESS')",
                account.id(), counterpartyId, amount, account.currency(), account.currency(), type, reference);
        post(account, amount, after, operation, type, reference);
    }

    /** Posts audit/outbox for an existing transaction, within the same balance-update transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(Account account, BigDecimal amount, BigDecimal after, String operation, String type, String reference) {
        Long transactionId = jdbc.queryForObject("SELECT transaction_id FROM LEDGER_TRANSACTION WHERE reference_no = ?", Long.class, reference);
        String payload;
        try {
            payload = json.writeValueAsString(Map.of("transactionId", transactionId, "referenceNo", reference,
                    "accountId", account.id(), "customerId", account.customerId(), "operation", operation,
                    "amount", amount.toPlainString(), "currency", account.currency(),
                    "beforeBalance", account.balance().toPlainString(), "afterBalance", after.toPlainString(),
                    "timestamp", LocalDateTime.now().toString()));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot record ledger event", ex); }
        jdbc.update("INSERT INTO OUTBOX_EVENT (transaction_id, event_type, payload, status) VALUES (?, 'TRANSACTION_SUCCESS', ?, 'PENDING')",
                transactionId, payload);
        jdbc.update("INSERT INTO AUDIT_LOG (customer_id, action, entity, details) VALUES (?, ?, 'ACCOUNT', ?)",
                account.customerId(), type, operation + " " + amount.toPlainString() + " " + account.currency()
                        + "; account " + account.number() + "; reference " + reference);
    }
}
