package com.bank.ledger.exception;

public class CurrencyMismatchException extends BusinessException {
    public CurrencyMismatchException(String message) {
        super(message, "CURRENCY_MISMATCH");
    }
}

