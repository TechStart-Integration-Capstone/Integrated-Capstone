package com.bank.t24.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T24 OFS Simulator sidecar.
 * Simulates Temenos T24 Core Banking responses.
 *
 * Endpoint: POST /ofs/process
 * Response formats:
 *   - Success:  { "status": "POSTED",   "ftReference": "FT202610040001", "ofsResponse": "FT202610040001/1" }
 *   - Reject:   { "status": "REJECTED", "ftReference": "FT202610040001", "ofsResponse": "FT202610040001/-1", "reason": "Account closed or invalid" }
 *   - Timeout:  Sleeps 2500 ms (> 2s SLA threshold)
 */
@RestController
@RequestMapping("/ofs")
public class T24SimulatorController {

    private static final Logger log = LoggerFactory.getLogger(T24SimulatorController.class);
    private static final AtomicLong FT_COUNTER = new AtomicLong(1000);
    private final Map<String, Map<String, Object>> processedMap = new ConcurrentHashMap<>();
    private final Random random = new Random();

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

        // Force explicit trigger test cases based on referenceNo prefixes
        if (referenceNo != null && referenceNo.contains("SIM-TIMEOUT")) {
            log.warn("[t24-simulator] SIM-TIMEOUT triggered — sleeping 2500ms");
            try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
        } else if (referenceNo != null && referenceNo.contains("SIM-REJECT")) {
            log.warn("[t24-simulator] SIM-REJECT triggered");
            Map<String, Object> rejectResp = Map.of(
                    "status", "REJECTED",
                    "ftReference", ftRef,
                    "ofsResponse", ftRef + "/-1",
                    "reason", "T24 Account Closed or Inactive (OFS /-1)"
            );
            if (referenceNo != null) processedMap.put(referenceNo, rejectResp);
            return ResponseEntity.ok(rejectResp);
        }

        // Standard probabilistic distribution if not explicitly forced
        int roll = random.nextInt(100);
        if (roll < 90) {
            // 90% Success
            Map<String, Object> successResp = Map.of(
                    "status", "POSTED",
                    "ftReference", ftRef,
                    "ofsResponse", ftRef + "/1"
            );
            if (referenceNo != null) processedMap.put(referenceNo, successResp);
            log.info("[t24-simulator] POSTED ref={} ft={}", referenceNo, ftRef);
            return ResponseEntity.ok(successResp);
        } else if (roll < 98) {
            // 8% Rejection
            Map<String, Object> rejectResp = Map.of(
                    "status", "REJECTED",
                    "ftReference", ftRef,
                    "ofsResponse", ftRef + "/-1",
                    "reason", "T24 Account Closed or Inactive (OFS /-1)"
            );
            if (referenceNo != null) processedMap.put(referenceNo, rejectResp);
            log.info("[t24-simulator] REJECTED ref={} ft={}", referenceNo, ftRef);
            return ResponseEntity.ok(rejectResp);
        } else {
            // 2% Timeout simulation (sleep 2500 ms)
            log.warn("[t24-simulator] Probabilistic TIMEOUT triggered for ref={} — sleeping 2500ms", referenceNo);
            try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
            Map<String, Object> timeoutResp = Map.of(
                    "status", "POSTED",
                    "ftReference", ftRef,
                    "ofsResponse", ftRef + "/1"
            );
            if (referenceNo != null) processedMap.put(referenceNo, timeoutResp);
            return ResponseEntity.ok(timeoutResp);
        }
    }

    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, String>> resetSimulator() {
        processedMap.clear();
        return ResponseEntity.ok(Map.of("message", "T24 Simulator reset successfully"));
    }
}
