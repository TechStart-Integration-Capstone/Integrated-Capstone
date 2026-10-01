package com.bank.ledger.exception;

public class LedgerPersistenceException extends RuntimeException {
    public LedgerPersistenceException(String message) {
        super(message);
    }

    public LedgerPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
