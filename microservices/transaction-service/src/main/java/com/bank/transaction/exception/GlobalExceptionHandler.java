package com.bank.transaction.exception;

import com.bank.transaction.dto.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetails> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest request) {

        int statusCode = ex.getStatusCode().value();
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/" + statusCode,
                HttpStatus.valueOf(statusCode).getReasonPhrase(),
                statusCode,
                reason,
                request.getRequestURI()
        );
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/malformed-payload",
                "Malformed JSON Schema",
                HttpStatus.BAD_REQUEST.value(),
                "Unable to parse incoming JSON schema. Ensure numeric fields and types match API specifications.",
                request.getRequestURI()
        );
        problem.addInvalidParam("payload", ex.getMostSpecificCause().getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetails> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {

        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/insufficient-funds",
                "Insufficient Account Balance",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest request) {

        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/account-not-found",
                "Account Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ProblemDetails> handleCurrencyMismatch(
            CurrencyMismatchException ex, HttpServletRequest request) {

        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/currency-mismatch",
                "Currency Incompatibility",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetails> handleLockContention(
            PessimisticLockingFailureException ex, HttpServletRequest request) {

        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/concurrency-lock-timeout",
                "Database Row Lock Contention",
                HttpStatus.CONFLICT.value(),
                "High concurrency detected. Lock acquisition timed out for account row. Please retry.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(LedgerPersistenceException.class)
    public ResponseEntity<ProblemDetails> handlePersistenceException(
            LedgerPersistenceException ex, HttpServletRequest request) {

        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/persistence-rollback",
                "Ledger Persistence Fault",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGenericException(
            Exception ex, HttpServletRequest request) {

        ProblemDetails problem = new ProblemDetails(
                "https://api.paypink.ph/errors/internal-server-error",
                "Internal Engine Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }
}
