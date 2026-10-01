package com.bank.ledger.controller;

import com.bank.ledger.dto.MutationRequest;
import com.bank.ledger.dto.StressTestRequest;
import com.bank.ledger.dto.StressTestResult;
import com.bank.ledger.exception.InsufficientFundsException;
import com.bank.ledger.model.oracle.Account;
import com.bank.ledger.repository.oracle.AccountRepository;
import com.bank.ledger.service.AccountService;
import com.bank.ledger.service.LedgerMutationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1/stress")
@CrossOrigin(origins = "*")
public class StressTestController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final LedgerMutationService ledgerMutationService;

    public StressTestController(AccountService accountService,
                                AccountRepository accountRepository,
                                LedgerMutationService ledgerMutationService) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.ledgerMutationService = ledgerMutationService;
    }

    /**
     * Requirement 1.B: Double-Spend Race Condition Stress Test
     * Spawns N concurrent threads trying to debit ₱50 from a ₱60 account simultaneously.
     * Proves @Lock(PESSIMISTIC_WRITE) serializes requests, preventing -40 PHP overdraft.
     */
    @PostMapping("/double-spend-test")
    public ResponseEntity<StressTestResult> runDoubleSpendStressTest(@RequestBody(required = false) StressTestRequest request) {
        if (request == null) {
            request = new StressTestRequest();
        }

        Long targetAccountId = request.getAccountId();
        if (targetAccountId == null) {
            // Find or use default stress account
            Account account = accountRepository.findAll().stream()
                    .filter(a -> "STRESS_TEST_ACCOUNT".equalsIgnoreCase(a.getAccountType()) || a.getAccountId() == 3L)
                    .findFirst()
                    .orElseGet(() -> accountRepository.findAll().get(0));
            targetAccountId = account.getAccountId();
        }

        // Reset starting balance to ₱60.0000
        BigDecimal startingBalance = request.getInitialBalance() != null ? request.getInitialBalance() : new BigDecimal("60.0000");
        accountService.resetAccountBalance(targetAccountId, startingBalance);

        int threadCount = request.getConcurrentThreads() > 0 ? request.getConcurrentThreads() : 10;
        BigDecimal debitAmount = request.getDebitAmountPerThread() != null ? request.getDebitAmountPerThread() : new BigDecimal("50.0000");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        List<StressTestResult.ThreadExecutionDetail> threadLogs = Collections.synchronizedList(new ArrayList<>());
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= threadCount; i++) {
            final int threadIndex = i;
            final Long accId = targetAccountId;
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize release to hit database concurrently
                    long tStart = System.currentTimeMillis();
                    MutationRequest mutation = new MutationRequest(accId, debitAmount, "DEBIT", "DEBIT", null, "PHP");
                    ledgerMutationService.mutateBalance(mutation, "stress_worker_" + threadIndex);
                    long latency = System.currentTimeMillis() - tStart;
                    threadLogs.add(new StressTestResult.ThreadExecutionDetail(
                            threadIndex,
                            "Thread-" + threadIndex,
                            "COMMITTED",
                            "Successfully debited ₱" + debitAmount.toPlainString() + " with @Lock(PESSIMISTIC_WRITE) isolation.",
                            latency
                    ));
                } catch (InsufficientFundsException ex) {
                    long latency = System.currentTimeMillis() - startTime;
                    threadLogs.add(new StressTestResult.ThreadExecutionDetail(
                            threadIndex,
                            "Thread-" + threadIndex,
                            "REJECTED_INSUFFICIENT_FUNDS",
                            "Safely rejected: Insufficient balance after serialized previous commit.",
                            latency
                    ));
                } catch (Exception ex) {
                    long latency = System.currentTimeMillis() - startTime;
                    threadLogs.add(new StressTestResult.ThreadExecutionDetail(
                            threadIndex,
                            "Thread-" + threadIndex,
                            "ERROR",
                            "Execution error: " + ex.getMessage(),
                            latency
                    ));
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();

        try {
            endGate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        long totalDurationMs = System.currentTimeMillis() - startTime;

        // Fetch final balance
        Account finalAccount = accountRepository.findById(targetAccountId).orElseThrow();
        BigDecimal actualFinalBalance = finalAccount.getCurrentBalance();
        BigDecimal expectedFinalBalance = startingBalance.subtract(debitAmount); // Exactly ₱10.0000

        long successCount = threadLogs.stream().filter(t -> "COMMITTED".equals(t.getStatus())).count();
        long rejectedCount = threadLogs.stream().filter(t -> "REJECTED_INSUFFICIENT_FUNDS".equals(t.getStatus())).count();

        StressTestResult result = new StressTestResult();
        result.setAccountId(targetAccountId);
        result.setStartingBalance(startingBalance);
        result.setExpectedFinalBalance(expectedFinalBalance);
        result.setActualFinalBalance(actualFinalBalance);
        result.setTotalAttemptedRequests(threadCount);
        result.setSuccessfulRequests((int) successCount);
        result.setRejectedRequests((int) rejectedCount);
        result.setRaceConditionPrevented(actualFinalBalance.compareTo(BigDecimal.ZERO) >= 0 && successCount == 1);
        result.setTotalExecutionTimeMs(totalDurationMs);
        result.setMeasuredTps(totalDurationMs > 0 ? (threadCount / (double) totalDurationMs) * 1000.0 : threadCount);
        result.setThreadLogs(threadLogs);

        return ResponseEntity.ok(result);
    }
}
