package com.bank.auth.banking;

import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Map;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth/banking/reports")
public class TransactionReportController {
    private final TransactionReportService reports;
    public TransactionReportController(TransactionReportService reports) {this.reports=reports;}
    @GetMapping(value="/transactions.pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@RequestHeader(value="Authorization",required=false) String token,
        @RequestParam long accountId,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        byte[] pdf=reports.generate(token,accountId,from,to);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"PayPink-Transactions-"+from+"-to-"+to+".pdf\"")
            .body(pdf);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(Map.of("message",ex.getReason()==null?"Unable to generate report.":ex.getReason()));
    }
}
