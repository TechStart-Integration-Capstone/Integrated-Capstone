package com.bank.loan.controller;

import com.bank.loan.dto.LoanDtos.*;
import com.bank.loan.exception.LoanException;
import com.bank.loan.service.LoanApplicationService;
import com.bank.loan.service.LoanCreditLimitService;
import com.bank.loan.service.LoanDisbursementService;
import com.bank.loan.service.LoanEodService;
import com.bank.loan.service.LoanQueryService;
import com.bank.loan.service.LoanRepaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Loan API, reached through the gateway at /api/v1/loans/** (StripPrefix → /loans/**).
 *
 * Identity comes from the trusted X-Auth-Customer-Id header that api-gateway sets after verifying the JWT
 * (and strips from incoming requests). loan-service never parses JWTs itself.
 */
@RestController
@RequestMapping("/loans")
public class LoanController {

    private final LoanApplicationService applications;
    private final LoanDisbursementService disbursements;
    private final LoanRepaymentService repayments;
    private final LoanQueryService queries;
    private final LoanEodService eod;
    private final LoanCreditLimitService creditLimit;

    public LoanController(LoanApplicationService applications, LoanDisbursementService disbursements,
                          LoanRepaymentService repayments, LoanQueryService queries, LoanEodService eod,
                          LoanCreditLimitService creditLimit) {
        this.applications = applications;
        this.disbursements = disbursements;
        this.repayments = repayments;
        this.queries = queries;
        this.eod = eod;
        this.creditLimit = creditLimit;
    }

    @PostMapping("/applications")
    public ResponseEntity<ApplicationResponse> apply(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ApplyRequest request) {
        var outcome = applications.apply(customerId(customerHeader), idempotencyKey, request);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.response());
    }

    @PostMapping("/applications/{referenceNo}/accept")
    public ResponseEntity<LoanSummary> accept(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String referenceNo) {
        LoanSummary loan = disbursements.accept(customerId(customerHeader), referenceNo, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(loan);
    }

    @GetMapping
    public List<LoanSummary> myLoans(@RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader) {
        return queries.myLoans(customerId(customerHeader));
    }

    /** Credit limit and how much the customer can still borrow. */
    @GetMapping("/eligibility")
    public Eligibility eligibility(@RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader) {
        return creditLimit.eligibility(customerId(customerHeader));
    }

    @GetMapping("/{loanId}/schedule")
    public ScheduleResponse schedule(@RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
                                     @PathVariable Long loanId) {
        return queries.schedule(customerId(customerHeader), loanId);
    }

    @PostMapping("/{loanId}/repayments")
    public ResponseEntity<RepaymentResponse> repay(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable Long loanId,
            @Valid @RequestBody RepaymentRequest request) {
        var outcome = repayments.repay(customerId(customerHeader), loanId, idempotencyKey, request.amount(), correlationId);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.response());
    }

    /** Admin only (also enforced at the gateway). Runs the overdue job for a business date, for the demo. */
    @PostMapping("/eod/run")
    public EodResult runEod(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        if (roles == null || !List.of(roles.split(",")).contains("ROLE_ADMIN")) {
            throw new LoanException(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "Administrator access required.");
        }
        return eod.run(businessDate);
    }

    private static Long customerId(String header) {
        try {
            if (header != null && !header.isBlank()) {
                long id = Long.parseLong(header.trim());
                if (id > 0) return id;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new LoanException(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", "Please sign in to continue.");
    }
}
