package com.bank.t24.dto;

public class T24TransferResponse {

    private String status;           // "POSTED", "REJECTED", "PROCESSING", "TIMEOUT"
    private String ftReference;       // e.g. "FT202610040001"
    private String ofsResponse;       // Raw OFS string e.g. "FT202610040001/1"
    private String reason;            // Failure reason if rejected
    private boolean cachedResponse;   // True if returned from idempotency store

    public T24TransferResponse() {}

    public T24TransferResponse(String status, String ftReference, String ofsResponse, String reason, boolean cachedResponse) {
        this.status = status;
        this.ftReference = ftReference;
        this.ofsResponse = ofsResponse;
        this.reason = reason;
        this.cachedResponse = cachedResponse;
    }

    public static T24TransferResponse success(String ftReference, String ofsResponse, boolean cachedResponse) {
        return new T24TransferResponse("POSTED", ftReference, ofsResponse, null, cachedResponse);
    }

    public static T24TransferResponse rejected(String ftReference, String ofsResponse, String reason) {
        return new T24TransferResponse("REJECTED", ftReference, ofsResponse, reason, false);
    }

    public static T24TransferResponse timeout(String reason) {
        return new T24TransferResponse("PROCESSING", null, null, reason, false);
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFtReference() { return ftReference; }
    public void setFtReference(String ftReference) { this.ftReference = ftReference; }

    public String getOfsResponse() { return ofsResponse; }
    public void setOfsResponse(String ofsResponse) { this.ofsResponse = ofsResponse; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isCachedResponse() { return cachedResponse; }
    public void setCachedResponse(boolean cachedResponse) { this.cachedResponse = cachedResponse; }
}
