package com.bank.transaction.orchestrator.interest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/** PostgreSQL access is strictly SELECT/INSERT. A daily batch commits independently of Azure SQL. */
public class InterestAccrualStore {
    public record Accrual(long accountId, BigDecimal balance, BigDecimal rate, BigDecimal amount) {}
    public record MonthlyTotal(long accountId, BigDecimal amount) {}
    public record Resolution(String mode, String actor, String reason, String source, String requestHash) {}
    public record Proposal(UUID proposalId, LocalDate businessDate, String preparedBy,
                           InterestRecoveryRequest request, String requestHash, String approvedBy,
                           java.time.OffsetDateTime preparedAt, java.time.OffsetDateTime approvedAt, String approvalReason) {
        public String getStatus() { return approvedBy == null ? "PENDING_APPROVAL" : "APPROVED"; }
    }
    public record ProposalSummary(UUID proposalId, LocalDate businessDate, String preparedBy,
                                  java.time.OffsetDateTime preparedAt, String status, String sourceReference, int accountCount) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper = new ObjectMapper();

    public InterestAccrualStore(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public boolean completed(LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (SELECT 1 FROM interest_accrual_batch b WHERE business_date = ?
                    AND (resolution_type = 'SNAPSHOT' OR (resolution_type = 'BACKFILL'
                        AND EXISTS (SELECT 1 FROM interest_backfill_approval a WHERE a.proposal_id = b.proposal_id))))
                """,
                Boolean.class, Date.valueOf(date)));
    }

    public void append(LocalDate date, List<Accrual> rows) {
        append(date, rows, null);
    }

    public Resolution resolution(LocalDate date) {
        var rows = jdbc.query("""
                SELECT resolution_type, resolved_by, resolution_reason, source_reference, request_hash
                FROM interest_accrual_batch WHERE business_date = ?
                """, (rs, n) -> new Resolution(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), Date.valueOf(date));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void append(LocalDate date, List<Accrual> rows, Resolution resolution) {
        append(date, rows, resolution, null);
    }

    private void append(LocalDate date, List<Accrual> rows, Resolution resolution, UUID proposalId) {
        transaction.executeWithoutResult(status -> {
            jdbc.batchUpdate("""
                    INSERT INTO interest_accrual (account_id, business_date, eod_balance, rate, interest_amount)
                    VALUES (?, ?, ?, ?, ?)
                    """, rows, 500, (ps, row) -> {
                ps.setLong(1, row.accountId());
                ps.setDate(2, Date.valueOf(date));
                ps.setBigDecimal(3, row.balance());
                ps.setBigDecimal(4, row.rate());
                ps.setBigDecimal(5, row.amount());
            });
            if (resolution == null) {
                jdbc.update("INSERT INTO interest_accrual_batch (business_date, account_count) VALUES (?, ?)",
                        Date.valueOf(date), rows.size());
            } else {
                jdbc.update("""
                        INSERT INTO interest_accrual_batch (business_date, account_count, resolution_type,
                            resolved_by, resolution_reason, source_reference, request_hash, proposal_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, Date.valueOf(date), rows.size(), resolution.mode(), resolution.actor(),
                        resolution.reason(), resolution.source(), resolution.requestHash(), proposalId);
            }
        });
    }

    public Set<LocalDate> completedDates(LocalDate start, LocalDate end) {
        return new HashSet<>(jdbc.query("""
                SELECT business_date FROM interest_accrual_batch b WHERE business_date BETWEEN ? AND ?
                    AND (resolution_type = 'SNAPSHOT' OR (resolution_type = 'BACKFILL'
                        AND EXISTS (SELECT 1 FROM interest_backfill_approval a WHERE a.proposal_id = b.proposal_id)))
                """, (rs, n) -> rs.getDate(1).toLocalDate(), Date.valueOf(start), Date.valueOf(end)));
    }

    public List<MonthlyTotal> monthlyTotals(LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT account_id, ROUND(SUM(interest_amount), 2) AS total_monthly_interest
                FROM interest_accrual WHERE business_date BETWEEN ? AND ?
                GROUP BY account_id ORDER BY account_id
                """, (rs, n) -> new MonthlyTotal(rs.getLong(1), rs.getBigDecimal(2)),
                Date.valueOf(start), Date.valueOf(end));
    }

    public Proposal prepare(LocalDate date, InterestRecoveryRequest request, String actor, String hash) {
        // Caller holds the cross-instance ledger lock. PostgreSQL ON CONFLICT is incompatible
        // with the immutable table's UPDATE rule, so replay the existing proposal explicitly.
        var existing = jdbc.query("SELECT proposal_id FROM interest_backfill_proposal WHERE business_date = ? AND request_hash = ?",
                (rs, n) -> rs.getObject(1, UUID.class), Date.valueOf(date), hash);
        if (!existing.isEmpty()) return proposal(existing.get(0));
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO interest_backfill_proposal (proposal_id, business_date, prepared_by, request_hash, request_json)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                    """, id, Date.valueOf(date), actor, hash, mapper.writeValueAsString(request));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize backfill proposal", ex); }
        return proposal(id);
    }

    public Proposal proposal(UUID id) {
        var proposals = jdbc.query("""
                SELECT p.*, a.approved_by, a.approved_at, a.approval_reason FROM interest_backfill_proposal p
                LEFT JOIN interest_backfill_approval a ON a.proposal_id = p.proposal_id WHERE p.proposal_id = ?
                """, (rs, n) -> {
            try {
                return new Proposal(rs.getObject("proposal_id", UUID.class), rs.getDate("business_date").toLocalDate(),
                        rs.getString("prepared_by"), mapper.readValue(rs.getString("request_json"), InterestRecoveryRequest.class),
                        rs.getString("request_hash"), rs.getString("approved_by"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class),
                        rs.getObject("approved_at", java.time.OffsetDateTime.class), rs.getString("approval_reason"));
            } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot read backfill proposal", ex); }
        }, id);
        if (proposals.isEmpty()) throw new IllegalArgumentException("Unknown backfill proposal");
        return proposals.get(0);
    }

    public List<ProposalSummary> proposals(LocalDate first, LocalDate end) {
        return jdbc.query("""
                SELECT p.*, a.approved_by,
                    EXISTS (SELECT 1 FROM interest_accrual_batch b WHERE b.business_date = p.business_date) AS sealed
                FROM interest_backfill_proposal p LEFT JOIN interest_backfill_approval a USING (proposal_id)
                WHERE p.business_date BETWEEN ? AND ? ORDER BY p.created_at DESC, p.proposal_id
                """, (rs, n) -> {
            try {
                var request = mapper.readTree(rs.getString("request_json"));
                return new ProposalSummary(rs.getObject("proposal_id", UUID.class), rs.getDate("business_date").toLocalDate(),
                        rs.getString("prepared_by"), rs.getObject("created_at", java.time.OffsetDateTime.class),
                        rs.getString("approved_by") != null ? "APPROVED" : rs.getBoolean("sealed") ? "SUPERSEDED" : "PENDING_APPROVAL",
                        request.path("sourceReference").asText(), request.path("accounts").size());
            } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot read backfill proposal", ex); }
        }, Date.valueOf(first), Date.valueOf(end));
    }

    public void approve(Proposal proposal, List<Accrual> rows, String actor, String reason) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO interest_backfill_approval (proposal_id, business_date, approved_by, approval_reason)
                    VALUES (?, ?, ?, ?)
                    """, proposal.proposalId(), Date.valueOf(proposal.businessDate()), actor, reason);
            append(proposal.businessDate(), rows, new Resolution("BACKFILL", proposal.preparedBy(),
                    proposal.request().reason(), proposal.request().sourceReference(), proposal.requestHash()), proposal.proposalId());
        });
    }
}
