package com.bank.loan.controller;

import com.bank.loan.dto.LoanDtos.*;
import com.bank.loan.exception.LoanException;
import com.bank.loan.service.LoanApplicationService;
import com.bank.loan.service.LoanCreditLimitService;
import com.bank.loan.service.LoanDisbursementService;
import com.bank.loan.service.LoanEodService;
import com.bank.loan.service.LoanQueryService;
import com.bank.loan.service.LoanRepaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Loans", description = "Loan application, credit evaluation, automated disbursement, and repayment schedules")
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

    @Operation(summary = "Apply for a loan", description = "Submits a new loan application. Evaluates credit limit eligibility and generates offer terms.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Loan application created successfully"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay of existing loan application"),
            @ApiResponse(responseCode = "400", description = "Invalid loan terms or exceeds credit limit"),
            @ApiResponse(responseCode = "401", description = "Customer authentication required")
    })
    @PostMapping("/applications")
    public ResponseEntity<ApplicationResponse> apply(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
            @Parameter(description = "Unique idempotency key to prevent duplicate applications", example = "idemp-apply-12345")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ApplyRequest request) {
        var outcome = applications.apply(customerId(customerHeader), idempotencyKey, request);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.response());
    }

    @Operation(summary = "Accept loan offer", description = "Accepts the loan offer terms and triggers disbursement into the customer deposit account via T24 Core.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Loan disbursed successfully"),
            @ApiResponse(responseCode = "400", description = "Application not in DECIDED state"),
            @ApiResponse(responseCode = "404", description = "Application reference not found")
    })
    @PostMapping("/applications/{referenceNo}/accept")
    public ResponseEntity<LoanSummary> accept(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
            @Parameter(description = "Unique idempotency key to prevent duplicate disbursement", example = "idemp-accept-12345")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Distributed trace correlation ID", example = "corr-disburse-98765")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Parameter(description = "Application reference number", example = "LN-APP-202610-001")
            @PathVariable String referenceNo) {
        LoanSummary loan = disbursements.accept(customerId(customerHeader), referenceNo, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(loan);
    }

    @Operation(summary = "List customer loans", description = "Retrieves all loans (active, pending, repaid, or defaulted) belonging to the authenticated customer.")
    @GetMapping
    public List<LoanSummary> myLoans(@Parameter(hidden = true) @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader) {
        return queries.myLoans(customerId(customerHeader));
    }

    /** Credit limit and how much the customer can still borrow. */
    @Operation(summary = "Check borrowing eligibility", description = "Calculates customer credit limit, current active exposure, and remaining available borrowing power.")
    @GetMapping("/eligibility")
    public Eligibility eligibility(@Parameter(hidden = true) @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader) {
        return creditLimit.eligibility(customerId(customerHeader));
    }

    @Operation(summary = "Get repayment schedule", description = "Retrieves the full amortization repayment schedule with principal, interest, and due dates for a loan.")
    @GetMapping("/{loanId}/schedule")
    public ScheduleResponse schedule(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
            @Parameter(description = "Loan ID", example = "1") @PathVariable Long loanId) {
        return queries.schedule(customerId(customerHeader), loanId);
    }

    @Operation(summary = "Make loan repayment", description = "Submits a repayment toward an active loan, deducting from the linked deposit account and recording the payment.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Repayment processed successfully"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay of existing repayment"),
            @ApiResponse(responseCode = "400", description = "Invalid repayment amount"),
            @ApiResponse(responseCode = "404", description = "Loan not found")
    })
    @PostMapping("/{loanId}/repayments")
    public ResponseEntity<RepaymentResponse> repay(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) String customerHeader,
            @Parameter(description = "Unique idempotency key for repayment", example = "idemp-repay-12345")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Distributed trace correlation ID", example = "corr-repay-98765")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Parameter(description = "Loan ID", example = "1")
            @PathVariable Long loanId,
            @Valid @RequestBody RepaymentRequest request) {
        var outcome = repayments.repay(customerId(customerHeader), loanId, idempotencyKey, request.amount(), correlationId);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.response());
    }

    /** Admin only (also enforced at the gateway). Runs the overdue job for a business date, for the demo. */
    @Operation(summary = "Run loan End-of-Day (EOD) job (Admin)", description = "Executes the loan EOD process for a business date, flagging overdue installments and calculating late charges.")
    @PostMapping("/eod/run")
    public EodResult runEod(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Business date to process (YYYY-MM-DD)", example = "2026-10-08")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        if (roles == null || !List.of(roles.split(",")).contains("ROLE_ADMIN")) {
            throw new LoanException(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "Administrator access required.");
        }
        return eod.run(businessDate);
    }

    /** Admin only. Resets a failed application back to DECIDED status so it can be re-opened. */
    @Operation(summary = "Reset loan application status (Admin)", description = "Resets a failed or expired loan application back to DECIDED status for re-evaluation.")
    @PostMapping("/applications/{referenceNo}/reset")
    public ResponseEntity<java.util.Map<String, String>> resetApplication(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Application reference number", example = "LN-APP-202610-001")
            @PathVariable String referenceNo) {
        if (roles == null || !List.of(roles.split(",")).contains("ROLE_ADMIN")) {
            throw new LoanException(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "Administrator access required.");
        }
        disbursements.adminResetApplication(referenceNo);
        return ResponseEntity.ok(java.util.Map.of("message", "Application " + referenceNo + " status reset to DECIDED."));
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
