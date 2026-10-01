package com.bank.ledger.exception;

import com.bank.ledger.dto.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Requirement 1.A: Intercept JSR-380 validation faults and format as RFC-7807 Problem Details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/validation-error",
                "Payload Validation Fault",
                HttpStatus.BAD_REQUEST.value(),
                "The incoming ledger mutation request failed boundary validation constraints.",
                request.getRequestURI()
        );

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            problem.addInvalidParam(
                    fieldError.getField(),
                    fieldError.getDefaultMessage(),
                    fieldError.getRejectedValue()
            );
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Requirement 1.A: Malformed JSON schemas or unparseable payloads.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/malformed-payload",
                "Malformed JSON Schema",
                HttpStatus.BAD_REQUEST.value(),
                "Unable to parse incoming JSON schema. Ensure numeric fields and types match API specifications.",
                request.getRequestURI()
        );
        problem.addInvalidParam("payload", ex.getMostSpecificCause().getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetails> handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/insufficient-funds",
                "Insufficient Account Balance",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/account-not-found",
                "Account Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ProblemDetails> handleCurrencyMismatch(CurrencyMismatchException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/currency-mismatch",
                "Currency Incompatibility",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<ProblemDetails> handleInvalidTransfer(InvalidTransferException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/invalid-transfer",
                "Invalid Transfer Request",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetails> handleLockContention(PessimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/concurrency-lock-timeout",
                "Database Row Lock Contention",
                HttpStatus.CONFLICT.value(),
                "High concurrency detected. Lock acquisition timed out for account row. Please retry.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(LedgerPersistenceException.class)
    public ResponseEntity<ProblemDetails> handlePersistenceException(LedgerPersistenceException ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/persistence-rollback",
                "Ledger Persistence Fault",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGenericException(Exception ex, HttpServletRequest request) {
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/internal-server-error",
                "Internal Engine Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
