package com.bank.transaction.controller;

import com.bank.transaction.dto.ActivityItem;
import com.bank.transaction.service.TransactionActivityService;
import com.bank.transaction.service.TransactionStatementReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * PayPink 2.0 — Customer Transaction Query & Statements Controller (Phase 4).
 * Serves transaction history feeds and downloadable PDF account statements.
 */
@Tag(name = "Transaction History & Statements", description = "CQRS transaction activity feed and downloadable PDF account statements")
@RestController
@RequestMapping("/api/v1/transactions")
@CrossOrigin(origins = "*")
public class TransactionQueryController {

    private final TransactionActivityService activityService;
    private final TransactionStatementReportService reportService;

    public TransactionQueryController(TransactionActivityService activityService,
                                      TransactionStatementReportService reportService) {
        this.activityService = activityService;
        this.reportService = reportService;
    }

    @Operation(summary = "Get transaction activity feed", description = "Retrieves unified transaction history (remittances, deposits, loans) for the customer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction activity feed returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid customer identity")
    })
    @GetMapping("/activity")
    public ResponseEntity<List<ActivityItem>> getCustomerActivity(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Admin override: customer ID", example = "2")
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader) {

        Long customerId = resolveCustomerId(customerIdHeader, customerIdParam, rolesHeader);
        return ResponseEntity.ok(activityService.getCustomerActivity(customerId));
    }

    public ResponseEntity<List<ActivityItem>> getCustomerActivity(Long customerIdHeader, Long customerIdParam) {
        return getCustomerActivity(customerIdHeader, customerIdParam, null);
    }

    @Operation(summary = "Download PDF account statement", description = "Generates and streams a downloadable PDF statement of account activity within the specified date range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF statement generated and returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid customer identity")
    })
    @GetMapping(value = "/reports/transactions.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReport(
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @Parameter(description = "Admin override: customer ID", example = "2")
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Auth-Roles", required = false) String rolesHeader,
            @Parameter(description = "Account ID", example = "1")
            @RequestParam long accountId,
            @Parameter(description = "From date (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "To date (YYYY-MM-DD)", example = "2026-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Long customerId = resolveCustomerId(customerIdHeader, customerIdParam, rolesHeader);

        byte[] pdf = reportService.generatePdf(customerId, accountId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PayPink-Transactions-" + from + "-to-" + to + ".pdf\"")
                .body(pdf);
    }

    public ResponseEntity<byte[]> downloadReport(
            Long customerIdHeader,
            Long customerIdParam,
            long accountId,
            LocalDate from,
            LocalDate to) {
        return downloadReport(customerIdHeader, customerIdParam, null, accountId, from, to);
    }

    private Long resolveCustomerId(Long customerIdHeader, Long customerIdParam, String rolesHeader) {
        boolean isAdmin = rolesHeader != null && java.util.Arrays.stream(rolesHeader.split(",")).map(String::trim).anyMatch("ROLE_ADMIN"::equals);
        if (isAdmin && customerIdParam != null) {
            return customerIdParam;
        }
        if (customerIdHeader != null) {
            return customerIdHeader;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing customer identity.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Request failed."));
    }
}
