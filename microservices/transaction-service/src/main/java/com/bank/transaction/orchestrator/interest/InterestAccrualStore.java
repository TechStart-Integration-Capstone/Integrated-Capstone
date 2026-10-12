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

    public void ensureSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS interest_accrual (
                    accrual_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    account_id BIGINT NOT NULL,
                    business_date DATE NOT NULL,
                    eod_balance NUMERIC(18,4) NOT NULL CHECK (eod_balance >= 0),
                    rate NUMERIC(7,4) NOT NULL CHECK (rate >= 0),
                    interest_amount NUMERIC(18,6) NOT NULL CHECK (interest_amount >= 0),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_account_business_date UNIQUE (account_id, business_date)
                );
                CREATE INDEX IF NOT EXISTS idx_interest_accrual_date ON interest_accrual(business_date, account_id);
                CREATE TABLE IF NOT EXISTS interest_accrual_batch (
                    business_date DATE PRIMARY KEY,
                    account_count INTEGER NOT NULL CHECK (account_count >= 0),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE OR REPLACE RULE no_update_interest_accrual AS ON UPDATE TO interest_accrual DO INSTEAD NOTHING;
                CREATE OR REPLACE RULE no_delete_interest_accrual AS ON DELETE TO interest_accrual DO INSTEAD NOTHING;
                CREATE OR REPLACE RULE no_update_interest_batch AS ON UPDATE TO interest_accrual_batch DO INSTEAD NOTHING;
                CREATE OR REPLACE RULE no_delete_interest_batch AS ON DELETE TO interest_accrual_batch DO INSTEAD NOTHING;
                CREATE OR REPLACE FUNCTION reject_interest_truncate() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'Interest audit tables are append-only';
                END;
                $$;
                DROP TRIGGER IF EXISTS interest_accrual_no_truncate ON interest_accrual;
                CREATE TRIGGER interest_accrual_no_truncate BEFORE TRUNCATE ON interest_accrual
                    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();
                DROP TRIGGER IF EXISTS interest_batch_no_truncate ON interest_accrual_batch;
                CREATE TRIGGER interest_batch_no_truncate BEFORE TRUNCATE ON interest_accrual_batch
                    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();
                CREATE OR REPLACE FUNCTION guard_interest_batch() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    PERFORM pg_advisory_xact_lock(72419, NEW.business_date - DATE '2000-01-01');
                    IF TG_TABLE_NAME = 'interest_accrual' THEN
                        IF EXISTS (SELECT 1 FROM interest_accrual_batch WHERE business_date = NEW.business_date) THEN
                            RAISE EXCEPTION 'Interest snapshot for % is already complete', NEW.business_date;
                        END IF;
                    ELSE
                        IF NEW.account_count <> (SELECT COUNT(*) FROM interest_accrual WHERE business_date = NEW.business_date) THEN
                            RAISE EXCEPTION 'Interest snapshot row count does not match batch';
                        END IF;
                    END IF;
                    RETURN NEW;
                END;
                $$;
                DROP TRIGGER IF EXISTS interest_accrual_guard ON interest_accrual;
                CREATE TRIGGER interest_accrual_guard BEFORE INSERT ON interest_accrual
                    FOR EACH ROW EXECUTE FUNCTION guard_interest_batch();
                DROP TRIGGER IF EXISTS interest_batch_guard ON interest_accrual_batch;
                CREATE TRIGGER interest_batch_guard BEFORE INSERT ON interest_accrual_batch
                    FOR EACH ROW EXECUTE FUNCTION guard_interest_batch();
                ALTER TABLE interest_accrual_batch
                    ADD COLUMN IF NOT EXISTS resolution_type VARCHAR(16) NOT NULL DEFAULT 'SNAPSHOT',
                    ADD COLUMN IF NOT EXISTS resolved_by VARCHAR(120),
                    ADD COLUMN IF NOT EXISTS resolution_reason VARCHAR(1000),
                    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(255),
                    ADD COLUMN IF NOT EXISTS request_hash CHAR(64);
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_interest_resolution'
                                   AND conrelid = 'interest_accrual_batch'::regclass) THEN
                        ALTER TABLE interest_accrual_batch ADD CONSTRAINT ck_interest_resolution CHECK (
                            (resolution_type = 'SNAPSHOT' AND resolved_by IS NULL AND resolution_reason IS NULL
                                AND source_reference IS NULL AND request_hash IS NULL)
                            OR
                            (resolution_type IN ('BACKFILL', 'WAIVER') AND resolved_by IS NOT NULL AND length(trim(resolved_by)) > 0
                                AND resolution_reason IS NOT NULL AND length(trim(resolution_reason)) > 0
                                AND source_reference IS NOT NULL AND length(trim(source_reference)) > 0
                                AND request_hash IS NOT NULL AND request_hash ~ '^[0-9a-f]{64}$'
                                AND (resolution_type <> 'WAIVER' OR account_count = 0))
                        );
                    END IF;
                END;
                $$;
                CREATE TABLE IF NOT EXISTS interest_backfill_proposal (
                    proposal_id UUID PRIMARY KEY,
                    business_date DATE NOT NULL,
                    prepared_by VARCHAR(120) NOT NULL CHECK (length(trim(prepared_by)) > 0),
                    request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
                    request_json JSONB NOT NULL CHECK (request_json->>'mode' = 'BACKFILL'),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (business_date, request_hash),
                    UNIQUE (proposal_id, business_date)
                );
                CREATE TABLE IF NOT EXISTS interest_backfill_approval (
                    proposal_id UUID PRIMARY KEY,
                    business_date DATE NOT NULL UNIQUE,
                    approved_by VARCHAR(120) NOT NULL CHECK (length(trim(approved_by)) > 0),
                    approval_reason VARCHAR(1000) NOT NULL CHECK (length(trim(approval_reason)) > 0),
                    approved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (proposal_id, business_date) REFERENCES interest_backfill_proposal(proposal_id, business_date)
                );
                ALTER TABLE interest_accrual_batch ADD COLUMN IF NOT EXISTS proposal_id UUID
                    REFERENCES interest_backfill_proposal(proposal_id);
                CREATE OR REPLACE RULE no_update_interest_proposal AS ON UPDATE TO interest_backfill_proposal DO INSTEAD NOTHING;
                CREATE OR REPLACE RULE no_delete_interest_proposal AS ON DELETE TO interest_backfill_proposal DO INSTEAD NOTHING;
                CREATE OR REPLACE RULE no_update_interest_approval AS ON UPDATE TO interest_backfill_approval DO INSTEAD NOTHING;
                CREATE OR REPLACE RULE no_delete_interest_approval AS ON DELETE TO interest_backfill_approval DO INSTEAD NOTHING;
                DROP TRIGGER IF EXISTS interest_proposal_no_truncate ON interest_backfill_proposal;
                CREATE TRIGGER interest_proposal_no_truncate BEFORE TRUNCATE ON interest_backfill_proposal
                    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();
                DROP TRIGGER IF EXISTS interest_approval_no_truncate ON interest_backfill_approval;
                CREATE TRIGGER interest_approval_no_truncate BEFORE TRUNCATE ON interest_backfill_approval
                    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();
                DROP TRIGGER IF EXISTS interest_approval_guard ON interest_backfill_approval;
                DROP FUNCTION IF EXISTS guard_interest_approval();
                CREATE OR REPLACE FUNCTION guard_interest_resolution() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.resolution_type = 'WAIVER' THEN
                        RAISE EXCEPTION 'Interest waivers are not permitted; missing days remain unresolved';
                    ELSIF NEW.resolution_type = 'BACKFILL' THEN
                        IF NOT EXISTS (
                            SELECT 1 FROM interest_backfill_proposal p JOIN interest_backfill_approval a USING (proposal_id, business_date)
                            WHERE p.proposal_id = NEW.proposal_id AND p.business_date = NEW.business_date
                                AND p.request_hash = NEW.request_hash AND p.prepared_by = NEW.resolved_by
                                AND p.request_json->>'reason' = NEW.resolution_reason
                                AND p.request_json->>'sourceReference' = NEW.source_reference
                                AND jsonb_array_length(p.request_json->'accounts') = NEW.account_count
                        ) THEN
                            RAISE EXCEPTION 'Backfill requires a separately approved matching proposal';
                        END IF;
                    ELSIF NEW.resolution_type <> 'SNAPSHOT' OR NEW.proposal_id IS NOT NULL THEN
                        RAISE EXCEPTION 'Invalid interest resolution';
                    END IF;
                    RETURN NEW;
                END;
                $$;
                DROP TRIGGER IF EXISTS interest_resolution_guard ON interest_accrual_batch;
                CREATE TRIGGER interest_resolution_guard BEFORE INSERT ON interest_accrual_batch
                    FOR EACH ROW EXECUTE FUNCTION guard_interest_resolution();
                """);
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
