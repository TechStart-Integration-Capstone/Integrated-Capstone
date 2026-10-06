package com.bank.loan.exception;

import org.springframework.http.HttpStatus;

/** A loan API error, rendered as RFC-7807 problem details with type https://api.paypink.ph/errors/{type}. */
public class LoanException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String title;

    public LoanException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public static LoanException validation(String detail) {
        return new LoanException(HttpStatus.BAD_REQUEST, "validation-error", "Validation Error", detail);
    }

    public static LoanException accountNotOwned() {
        return new LoanException(HttpStatus.FORBIDDEN, "account-not-owned", "Account Not Owned",
                "The account does not belong to the signed-in customer.");
    }

    public static LoanException loanNotFound() {
        return new LoanException(HttpStatus.NOT_FOUND, "loan-not-found", "Loan Not Found",
                "No loan or application with that reference was found.");
    }

    public static LoanException offerExpired() {
        return new LoanException(HttpStatus.CONFLICT, "offer-expired", "Offer Expired",
                "This loan offer has expired. Please apply again.");
    }

    public static LoanException alreadyAccepted() {
        return new LoanException(HttpStatus.CONFLICT, "already-accepted", "Already Accepted",
                "This loan offer has already been accepted.");
    }

    public static LoanException offerDeclined() {
        return new LoanException(HttpStatus.CONFLICT, "offer-declined", "Offer Declined",
                "This application was declined and cannot be accepted.");
    }

    public static LoanException disbursementFailed() {
        return new LoanException(HttpStatus.UNPROCESSABLE_ENTITY, "disbursement-failed", "Disbursement Failed",
                "We couldn’t release this loan and nothing was credited to your account. Please apply again.");
    }

    public static LoanException creditLimitReached(String detail) {
        return new LoanException(HttpStatus.CONFLICT, "credit-limit-reached", "Credit Limit Reached", detail);
    }

    public static LoanException idempotencyConflict() {
        return new LoanException(HttpStatus.CONFLICT, "idempotency-conflict", "Idempotency Conflict",
                "This Idempotency-Key was already used for a different request.");
    }

    public static LoanException insufficientFunds() {
        return new LoanException(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds", "Insufficient Funds",
                "The account does not have enough available balance for this payment.");
    }

    public static LoanException coreUnavailable(String detail) {
        return new LoanException(HttpStatus.SERVICE_UNAVAILABLE, "core-unavailable", "Core Banking Unavailable", detail);
    }

    public HttpStatus getStatus() { return status; }
    public String getType() { return type; }
    public String getTitle() { return title; }
}
