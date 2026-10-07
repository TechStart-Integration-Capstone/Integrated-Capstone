-- Simulation policy: one admin may prepare and later approve the same proposal.
-- Preserve separate immutable records and all approval/completion integrity checks.
BEGIN;
DROP TRIGGER IF EXISTS interest_approval_guard ON interest_backfill_approval;
DROP FUNCTION IF EXISTS guard_interest_approval();
COMMIT;
