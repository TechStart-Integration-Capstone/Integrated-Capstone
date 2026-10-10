package com.bank.transaction.service;

import com.bank.transaction.dto.RemittanceRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.springframework.http.HttpStatus.*;

/** Uses durable external instructions for replay; never holds a DB lock over risk/core HTTP calls. */
@Service
public class ExternalSettlementService {
    public static final String CLEARING_ACCOUNT = "PH1000000EXT";
    private final JdbcTemplate jdbc;
    private final RemittanceOrchestratorService orchestrator;
    public ExternalSettlementService(JdbcTemplate jdbc, RemittanceOrchestratorService orchestrator) {
        this.jdbc = jdbc; this.orchestrator = orchestrator;
    }
    private record Instruction(long id, long source, long owner, BigDecimal amount, String type,
                               String status, LocalDateTime date, int postedEvents) {}

    public String settle(String reference, long customerId) {
        var rows = jdbc.query("""
                SELECT t.transaction_id,t.from_account_id,a.customer_id,t.amount,t.transaction_type,
                       t.status,t.transaction_date,
                       (SELECT COUNT(*) FROM dbo.OUTBOX_EVENT o WHERE o.transaction_id=t.transaction_id
                        AND o.event_type IN ('TRANSACTION_SUCCESS','REMITTANCE_COMPLETED'))
                FROM dbo.LEDGER_TRANSACTION t JOIN dbo.ACCOUNT a ON a.account_id=t.from_account_id
                WHERE t.reference_no=?
                """, (rs,n) -> new Instruction(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getBigDecimal(4),
                rs.getString(5),rs.getString(6),rs.getTimestamp(7).toLocalDateTime(),rs.getInt(8)), reference);
        if (rows.size()!=1) throw new ResponseStatusException(NOT_FOUND,"External instruction not found");
        var row=rows.get(0);
        if (row.owner()!=customerId) throw new ResponseStatusException(FORBIDDEN,"Instruction belongs to another customer");
        if (!RemittanceRequest.isExternalType(row.type()))
            throw new ResponseStatusException(BAD_REQUEST,"Not an external instruction");
        if (!"PENDING".equals(row.status())) return row.status();
        // Older PESONet versions could queue a status update after already debiting.
        if (row.postedEvents()>0) {
            jdbc.update("UPDATE dbo.LEDGER_TRANSACTION SET status='SUCCESS' WHERE transaction_id=? AND status='PENDING'",row.id());
            return "SUCCESS";
        }
        if (row.type().startsWith("EXT_PESONET_") && row.date().isAfter(LocalDateTime.now().minusSeconds(90)))
            return "PENDING";
        Integer clearing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.ACCOUNT WHERE account_number=? AND account_type='INTERNAL'
                AND currency='PHP' AND status='ACTIVE'
                """,Integer.class,CLEARING_ACCOUNT);
        if (clearing==null || clearing!=1)
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,"External clearing account is unavailable");
        var request = new RemittanceRequest(String.valueOf(row.source()),CLEARING_ACCOUNT,row.amount(),"PHP");
        request.setTransactionType(row.type());
        try {
            var result=orchestrator.processExternalRemittance(request,reference,customerId);
            return "POSTED".equalsIgnoreCase(result.getStatus()) ? "SUCCESS" : "PENDING";
        } catch (ResponseStatusException ex) {
            // Conflicts/timeouts remain pending: a concurrent request or the saga worker can still post.
            if (ex.getStatusCode().value()==422 || ex.getStatusCode().value()==400) {
                jdbc.update("UPDATE dbo.LEDGER_TRANSACTION SET status='FAILED' WHERE transaction_id=? AND status='PENDING'",row.id());
                return "FAILED";
            }
            throw ex;
        }
    }
}
