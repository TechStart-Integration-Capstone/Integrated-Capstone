package com.bank.ledger.exception;

public class InvalidTransferException extends BusinessException {
    public InvalidTransferException(String message) {
        super(message, "INVALID_TRANSFER");
    }
}
