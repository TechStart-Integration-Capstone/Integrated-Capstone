package com.bank.transaction.dto;

/**
 * Result of an internal transfer. status is one of POSTED | REJECTED | PENDING_CORE.
 * PENDING_CORE means the outcome is not final yet (T24 still processing); retry with the same key.
 */
public class InternalTransferResponse {

    public static final String REASON_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    private String status;
    private Long transactionId;
    private String ftReference;
    private String reason;

    public InternalTransferResponse() {}

    public InternalTransferResponse(String status, Long transactionId, String ftReference, String reason) {
        this.status = status;
        this.transactionId = transactionId;
        this.ftReference = ftReference;
        this.reason = reason;
    }

    public static InternalTransferResponse posted(Long transactionId, String ftReference) {
        return new InternalTransferResponse("POSTED", transactionId, ftReference, null);
    }

    public static InternalTransferResponse rejected(String reason) {
        return new InternalTransferResponse("REJECTED", null, null, reason);
    }

    public static InternalTransferResponse pendingCore(String ftReference, String reason) {
        return new InternalTransferResponse("PENDING_CORE", null, ftReference, reason);
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getFtReference() { return ftReference; }
    public void setFtReference(String ftReference) { this.ftReference = ftReference; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
