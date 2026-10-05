package com.bank.loan.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RFC-7807 problem details, same shape as the other PayPink services. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(LoanException.class)
    public ResponseEntity<Map<String, Object>> handleLoan(LoanException ex, HttpServletRequest request) {
        return problem(ex.getStatus(), ex.getType(), ex.getTitle(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, Object>> params = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("name", e.getField());
                    p.put("reason", e.getDefaultMessage());
                    return p;
                })
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Error",
                "The request failed validation.", request, params);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest request) {
        String detail = ex instanceof MissingRequestHeaderException m ? "Missing required header: " + m.getHeaderName()
                : ex instanceof MissingServletRequestParameterException p ? "Missing required parameter: " + p.getParameterName()
                : ex instanceof MethodArgumentTypeMismatchException t ? "Invalid value for " + t.getName()
                : "Malformed request body.";
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Error", detail, request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("[loan-service] Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Error",
                "The loan request could not be completed. Please try again.", request, null);
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String type, String title, String detail,
                                                        HttpServletRequest request, List<Map<String, Object>> invalidParams) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://api.paypink.ph/errors/" + type);
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("timestamp", Instant.now().toString());
        if (invalidParams != null && !invalidParams.isEmpty()) body.put("invalidParams", invalidParams);
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }
}
