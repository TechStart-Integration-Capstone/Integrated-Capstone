package com.bank.t24.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T24 OFS Simulator sidecar.
 * Simulates Temenos T24 Core Banking responses deterministically based on:
 *   1. OFS Message Validation (Application, Mode, Fields, Amount > 0, distinct accounts)
 *   2. Account Lifecycle Status (ACTIVE vs FROZEN vs CLOSED)
 *
 * Endpoint: POST /ofs/process
 */
@RestController
@RequestMapping("/ofs")
public class T24SimulatorController {

    private static final Logger log = LoggerFactory.getLogger(T24SimulatorController.class);
    private static final AtomicLong FT_COUNTER = new AtomicLong(1000);

    private final Map<String, Map<String, Object>> processedMap = new ConcurrentHashMap<>();
    private final Map<String, String> accountStatusMap = new ConcurrentHashMap<>();

    public record OfsParseResult(
            boolean valid,
            String errorMessage,
            String debitAccountNo,
            String creditAccountNo,
            BigDecimal amount,
            String currency
    ) {}

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processOfs(@RequestBody Map<String, Object> payload) {
        String referenceNo = (String) payload.get("referenceNo");
        String ofsMessage = (String) payload.get("ofsMessage");

        if (referenceNo != null && processedMap.containsKey(referenceNo)) {
            log.info("[t24-simulator] Replaying stored response for ref={}", referenceNo);
            return ResponseEntity.ok(processedMap.get(referenceNo));
        }

        long ftSeq = FT_COUNTER.incrementAndGet();
        String ftRef = "FT20261004" + ftSeq;

        // Explicit timeout test trigger (no random probability)
        if (referenceNo != null && referenceNo.contains("SIM-TIMEOUT")) {
            log.warn("[t24-simulator] Explicit SIM-TIMEOUT triggered for ref={} — sleeping 2500ms", referenceNo);
            try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
        }

        // Backward compatibility for explicit test reference prefix
        if (referenceNo != null && referenceNo.contains("SIM-REJECT")) {
            log.warn("[t24-simulator] SIM-REJECT triggered for ref={}", referenceNo);
            Map<String, Object> rejectResp = Map.of(
                    "status", "REJECTED",
                    "ftReference", ftRef,
                    "ofsResponse", ftRef + "/-1",
                    "reason", "T24 Account Closed or Inactive (OFS /-1)"
            );
            processedMap.put(referenceNo, rejectResp);
            return ResponseEntity.ok(rejectResp);
        }

        // 1. Validate OFS syntax and structure
        OfsParseResult parseResult = parseAndValidateOfs(ofsMessage);
        if (!parseResult.valid()) {
            log.warn("[t24-simulator] REJECTED ref={} due to invalid OFS message: {}", referenceNo, parseResult.errorMessage());
            Map<String, Object> rejectResp = Map.of(
                    "status", "REJECTED",
                    "ftReference", ftRef,
                    "ofsResponse", ftRef + "/-1",
                    "reason", "Invalid OFS string: " + parseResult.errorMessage() + " (OFS /-1)"
            );
            if (referenceNo != null) processedMap.put(referenceNo, rejectResp);
            return ResponseEntity.ok(rejectResp);
        }

        // 2. Validate Account Status (Closed or Frozen)
        String debitStatus = getAccountStatus(parseResult.debitAccountNo());
        String creditStatus = getAccountStatus(parseResult.creditAccountNo());

        if ("CLOSED".equalsIgnoreCase(debitStatus)) {
            return rejectAccount(referenceNo, ftRef, "T24 Account Closed: Debit account " + parseResult.debitAccountNo() + " is CLOSED (OFS /-1)");
        }
        if ("CLOSED".equalsIgnoreCase(creditStatus)) {
            return rejectAccount(referenceNo, ftRef, "T24 Account Closed: Credit account " + parseResult.creditAccountNo() + " is CLOSED (OFS /-1)");
        }
        if ("FROZEN".equalsIgnoreCase(debitStatus)) {
            return rejectAccount(referenceNo, ftRef, "T24 Account Frozen: Debit account " + parseResult.debitAccountNo() + " is FROZEN (OFS /-1)");
        }
        if ("FROZEN".equalsIgnoreCase(creditStatus)) {
            return rejectAccount(referenceNo, ftRef, "T24 Account Frozen: Credit account " + parseResult.creditAccountNo() + " is FROZEN (OFS /-1)");
        }

        // 3. OFS valid and accounts active -> POSTED (Success)
        Map<String, Object> successResp = Map.of(
                "status", "POSTED",
                "ftReference", ftRef,
                "ofsResponse", ftRef + "/1"
        );
        if (referenceNo != null) processedMap.put(referenceNo, successResp);
        log.info("[t24-simulator] POSTED ref={} ft={} debit={} credit={} amount={}",
                referenceNo, ftRef, parseResult.debitAccountNo(), parseResult.creditAccountNo(), parseResult.amount());
        return ResponseEntity.ok(successResp);
    }

    private ResponseEntity<Map<String, Object>> rejectAccount(String referenceNo, String ftRef, String reason) {
        log.warn("[t24-simulator] REJECTED ref={} — {}", referenceNo, reason);
        Map<String, Object> rejectResp = Map.of(
                "status", "REJECTED",
                "ftReference", ftRef,
                "ofsResponse", ftRef + "/-1",
                "reason", reason
        );
        if (referenceNo != null) processedMap.put(referenceNo, rejectResp);
        return ResponseEntity.ok(rejectResp);
    }

    public OfsParseResult parseAndValidateOfs(String ofsMessage) {
        if (ofsMessage == null || ofsMessage.trim().isEmpty()) {
            return new OfsParseResult(false, "OFS message is empty or null", null, null, null, null);
        }

        String trimmed = ofsMessage.trim();
        if (!trimmed.startsWith("FUNDS.TRANSFER,")) {
            return new OfsParseResult(false, "Missing or invalid application header: expected FUNDS.TRANSFER,", null, null, null, null);
        }
        if (!trimmed.contains("/I/PROCESS")) {
            return new OfsParseResult(false, "Missing operation mode: expected /I/PROCESS", null, null, null, null);
        }

        String debitAccount = null;
        String creditAccount = null;
        BigDecimal amount = null;
        String currency = null;

        String[] tokens = trimmed.split(",");
        for (String token : tokens) {
            if (token.contains("::")) {
                String[] kv = token.split("::", 2);
                String key = kv[0].trim();
                String val = kv.length > 1 ? kv[1].trim() : "";
                if ("DEBIT.ACCT.NO".equalsIgnoreCase(key)) {
                    debitAccount = val;
                } else if ("CREDIT.ACCT.NO".equalsIgnoreCase(key)) {
                    creditAccount = val;
                } else if ("AMOUNT".equalsIgnoreCase(key)) {
                    try {
                        amount = new BigDecimal(val);
                    } catch (Exception e) {
                        return new OfsParseResult(false, "Invalid numeric format for AMOUNT: " + val, null, null, null, null);
                    }
                } else if ("CURRENCY".equalsIgnoreCase(key)) {
                    currency = val;
                }
            }
        }

        if (debitAccount == null || debitAccount.isBlank()) {
            return new OfsParseResult(false, "Missing mandatory field: DEBIT.ACCT.NO", null, null, null, null);
        }
        if (creditAccount == null || creditAccount.isBlank()) {
            return new OfsParseResult(false, "Missing mandatory field: CREDIT.ACCT.NO", null, null, null, null);
        }
        if (debitAccount.equalsIgnoreCase(creditAccount)) {
            return new OfsParseResult(false, "Debit and credit accounts cannot be identical: " + debitAccount, debitAccount, creditAccount, amount, currency);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new OfsParseResult(false, "AMOUNT must be greater than zero", debitAccount, creditAccount, amount, currency);
        }
        if (currency == null || currency.isBlank()) {
            return new OfsParseResult(false, "Missing mandatory field: CURRENCY", debitAccount, creditAccount, amount, currency);
        }

        return new OfsParseResult(true, null, debitAccount, creditAccount, amount, currency);
    }

    public String getAccountStatus(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) return "CLOSED";

        // 1. In-memory registered status
        String registered = accountStatusMap.get(accountNo);
        if (registered != null) {
            return registered.toUpperCase();
        }

        // 2. Convention-based status detection (for automated tests and seeds)
        String upper = accountNo.toUpperCase();
        if (upper.contains("CLOSED") || upper.contains("INACTIVE")) {
            return "CLOSED";
        }
        if (upper.contains("FROZEN")) {
            return "FROZEN";
        }

        // 3. Default for all standard bank accounts
        return "ACTIVE";
    }

    @PostMapping("/account-status")
    public ResponseEntity<Map<String, String>> setAccountStatus(@RequestBody Map<String, String> payload) {
        String accountNo = payload.get("accountNo");
        String status = payload.get("status");
        if (accountNo == null || status == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "accountNo and status are required"));
        }
        accountStatusMap.put(accountNo, status.toUpperCase());
        return ResponseEntity.ok(Map.of("accountNo", accountNo, "status", status.toUpperCase()));
    }

    @GetMapping("/account-status/{accountNo}")
    public ResponseEntity<Map<String, String>> getAccountStatusEndpoint(@PathVariable String accountNo) {
        return ResponseEntity.ok(Map.of("accountNo", accountNo, "status", getAccountStatus(accountNo)));
    }

    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, String>> resetSimulator() {
        processedMap.clear();
        accountStatusMap.clear();
        return ResponseEntity.ok(Map.of("message", "T24 Simulator reset successfully"));
    }
}
