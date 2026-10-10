package com.bank.auth.banking;

/** Sender-only requests which have not acquired a ledger entry yet. */
final class UnpostedTransferQuery {
    private UnpostedTransferQuery() {}

    static final String STATUS = "CASE WHEN UPPER(r.status) IN ('FAILED','REJECTED') THEN 'FAILED' "
            + "WHEN UPPER(r.status) IN ('CANCELLED','CANCELED','REVERSED') THEN 'CANCELLED' ELSE 'PENDING' END";

    static final String ELIGIBLE = "(r.transaction_type = 'TRANSFER' OR r.transaction_type IS NULL) "
            + "AND UPPER(r.status) NOT IN ('POSTED','SUCCESS','COMPLETED') "
            + "AND NOT EXISTS (SELECT 1 FROM LEDGER_TRANSACTION posted WHERE posted.reference_no = r.reference_no "
            + "AND posted.from_account_id = r.source_account_id)";
}
