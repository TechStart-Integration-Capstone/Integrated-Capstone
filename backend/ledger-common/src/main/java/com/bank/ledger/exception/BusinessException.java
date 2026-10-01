package com.bank.ledger.exception;

public abstract class BusinessException extends RuntimeException {
    private final String reasonCode;

    public BusinessException(String message, String reasonCode) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public BusinessException(String message, String reasonCode, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
