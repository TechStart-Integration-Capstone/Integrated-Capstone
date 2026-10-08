package com.bank.transaction.controller;

import com.bank.transaction.dto.ActivityItem;
import com.bank.transaction.service.TransactionActivityService;
import com.bank.transaction.service.TransactionStatementReportService;
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

    @GetMapping("/activity")
    public ResponseEntity<List<ActivityItem>> getCustomerActivity(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam) {

        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing customer identity.");
        }
        return ResponseEntity.ok(activityService.getCustomerActivity(customerId));
    }

    @GetMapping(value = "/reports/transactions.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReport(
            @RequestHeader(value = "X-Auth-Customer-Id", required = false) Long customerIdHeader,
            @RequestParam(value = "customerId", required = false) Long customerIdParam,
            @RequestParam long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Long customerId = customerIdHeader != null ? customerIdHeader : customerIdParam;
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing customer identity.");
        }

        byte[] pdf = reportService.generatePdf(customerId, accountId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PayPink-Transactions-" + from + "-to-" + to + ".pdf\"")
                .body(pdf);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Request failed."));
    }
}
