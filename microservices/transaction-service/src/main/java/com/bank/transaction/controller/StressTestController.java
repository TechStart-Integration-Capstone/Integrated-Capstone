package com.bank.transaction.controller;

import com.bank.transaction.dto.MutationRequest;
import com.bank.transaction.dto.StressTestRequest;
import com.bank.transaction.dto.StressTestResult;
import com.bank.transaction.exception.InsufficientFundsException;
import com.bank.transaction.model.Account;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.service.AccountResetService;
import com.bank.transaction.service.LedgerMutationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Requirement 1.B — Double-Spend Race Condition Stress Test.
 *
 * Spawns N concurrent threads that all try to debit ₱50 from an account
 * reset to ₱60. Proves that @Lock(PESSIMISTIC_WRITE) serialises requests
 * at the Oracle XE row level, allowing exactly 1 commit and 0 overdrafts.
 *
 * Route:  POST /api/v1/stress/double-spend-test
 * Proxied: API Gateway → transaction-service:8083
 */
@RestController
@RequestMapping("/api/v1/stress")
@CrossOrigin(origins = "*")
public class StressTestController {

    private final AccountRepository     accountRepository;
    private final LedgerMutationService ledgerMutationService;
    private final AccountResetService   accountResetService;

    public StressTestController(AccountRepository accountRepository,
                                LedgerMutationService ledgerMutationService,
                                AccountResetService accountResetService) {
        this.accountRepository     = accountRepository;
        this.ledgerMutationService = ledgerMutationService;
        this.accountResetService   = accountResetService;
    }

    @PostMapping("/double-spend-test")
    public ResponseEntity<StressTestResult> runDoubleSpendStressTest(
            @RequestBody(required = false) StressTestRequest request) {

        if (request == null) {
            request = new StressTestRequest();
        }

        // ── 1. Resolve target account ─────────────────────────────────────────
        Long targetAccountId = request.getAccountId();
        if (targetAccountId == null) {
            Account stressAccount = accountRepository.findAll().stream()
                    .filter(a -> "STRESS_TEST_ACCOUNT".equalsIgnoreCase(a.getAccountType())
                              || Long.valueOf(3L).equals(a.getAccountId()))
                    .findFirst()
                    .orElseGet(() -> accountRepository.findAll().get(0));
            targetAccountId = stressAccount.getAccountId();
        }

        // ── 2. Reset account balance (proxy-safe @Transactional via injected service) ──
        BigDecimal startingBalance = (request.getInitialBalance() != null)
                ? request.getInitialBalance()
                : new BigDecimal("60.0000");
        accountResetService.resetBalance(targetAccountId, startingBalance);

        // ── 3. Prepare concurrency parameters ─────────────────────────────────
        int        threadCount = request.getConcurrentThreads() > 0 ? request.getConcurrentThreads() : 10;
        BigDecimal debitAmount = (request.getDebitAmountPerThread() != null)
                ? request.getDebitAmountPerThread()
                : new BigDecimal("50.0000");

        ExecutorService executor  = Executors.newFixedThreadPool(threadCount);
        CountDownLatch  startGate = new CountDownLatch(1);      // fire all threads at once
        CountDownLatch  endGate   = new CountDownLatch(threadCount);

        List<StressTestResult.ThreadExecutionDetail> threadLogs =
                Collections.synchronizedList(new ArrayList<>());
        long startTime = System.currentTimeMillis();

        // ── 4. Submit all threads before releasing start gate ─────────────────
        for (int i = 1; i <= threadCount; i++) {
            final int  idx   = i;
            final Long accId = targetAccountId;
            executor.submit(() -> {
                try {
                    startGate.await();                          // synchronised burst
                    long tStart = System.currentTimeMillis();

                    MutationRequest mutation = new MutationRequest(
                            accId, debitAmount, "DEBIT", "DEBIT", null, "PHP");
                    ledgerMutationService.mutateBalance(mutation, "stress_worker_" + idx);

                    long latency = System.currentTimeMillis() - tStart;
                    threadLogs.add(new StressTestResult.ThreadExecutionDetail(
                            idx, "Thread-" + idx,
                            "COMMITTED",
                            "Successfully debited \u20b1" + debitAmount.toPlainString()
                                + " with @Lock(PESSIMISTIC_WRITE) isolation.",
                            latency));

                } catch (InsufficientFundsException ex) {
                    long latency = System.currentTimeMillis() - startTime;
                    threadLogs.add(new StressTestResult.ThreadExecutionDetail(
                            idx, "Thread-" + idx,
                            "REJECTED_INSUFFICIENT_FUNDS",
                            "Safely rejected: Insufficient balance after serialised previous commit.",
                            latency));

                } catch (Exception ex) {
                    long latency = System.currentTimeMillis() - startTime;
                    threadLogs.add(new StressTestResult.ThreadExecutionDetail(
                            idx, "Thread-" + idx,
                            "ERROR",
                            "Execution error: " + ex.getMessage(),
                            latency));
                } finally {
                    endGate.countDown();
                }
            });
        }

        // ── 5. Release all threads simultaneously ─────────────────────────────
        startGate.countDown();
        try {
            endGate.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        long totalDurationMs = System.currentTimeMillis() - startTime;

        // ── 6. Read actual final balance from Oracle ──────────────────────────
        Account    finalAccount         = accountRepository.findById(targetAccountId).orElseThrow();
        BigDecimal actualFinalBalance   = finalAccount.getCurrentBalance();
        BigDecimal expectedFinalBalance = startingBalance.subtract(debitAmount); // ₱10.0000

        long successCount  = threadLogs.stream().filter(t -> "COMMITTED".equals(t.getStatus())).count();
        long rejectedCount = threadLogs.stream().filter(t -> "REJECTED_INSUFFICIENT_FUNDS".equals(t.getStatus())).count();

        // ── 7. Build result ───────────────────────────────────────────────────
        StressTestResult result = new StressTestResult();
        result.setAccountId(targetAccountId);
        result.setStartingBalance(startingBalance);
        result.setExpectedFinalBalance(expectedFinalBalance);
        result.setActualFinalBalance(actualFinalBalance);
        result.setTotalAttemptedRequests(threadCount);
        result.setSuccessfulRequests((int) successCount);
        result.setRejectedRequests((int) rejectedCount);
        result.setRaceConditionPrevented(
                actualFinalBalance.compareTo(BigDecimal.ZERO) >= 0 && successCount == 1);
        result.setTotalExecutionTimeMs(totalDurationMs);
        result.setMeasuredTps(totalDurationMs > 0
                ? (threadCount / (double) totalDurationMs) * 1000.0
                : threadCount);
        result.setThreadLogs(threadLogs);

        return ResponseEntity.ok(result);
    }
}
