package com.bank.ledger.service;

import com.bank.ledger.dto.MutationRequest;
import com.bank.ledger.dto.MutationResponse;
import com.bank.ledger.dto.TransactionOutboxPayload;
import com.bank.ledger.dto.TransactionOutboxPayload.EntryPayload;
import com.bank.ledger.exception.AccountNotFoundException;
import com.bank.ledger.exception.CurrencyMismatchException;
import com.bank.ledger.exception.InsufficientFundsException;
import com.bank.ledger.exception.InvalidTransferException;
import com.bank.ledger.exception.LedgerPersistenceException;
import com.bank.ledger.model.oracle.Account;
import com.bank.ledger.model.oracle.AuditLog;
import com.bank.ledger.model.oracle.OutboxEvent;
import com.bank.ledger.model.oracle.TransactionRecord;
import com.bank.ledger.repository.oracle.AccountRepository;
import com.bank.ledger.repository.oracle.AuditLogRepository;
import com.bank.ledger.repository.oracle.OutboxEventRepository;
import com.bank.ledger.repository.oracle.TransactionRepository;
import com.bank.ledger.security.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerMutationService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final IdempotencyService idempotencyService;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public LedgerMutationService(AccountRepository accountRepository,
                                 TransactionRepository transactionRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 AuditLogRepository auditLogRepository,
                                 IdempotencyService idempotencyService,
                                 TelemetryService telemetryService,
                                 ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyService = idempotencyService;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Core Mutation Engine:
     * Executes inside an atomic local ACID transaction on Oracle XE:
     * 1. Acquires @Lock(LockModeType.PESSIMISTIC_WRITE) on accounts in ascending ID order to prevent deadlocks.
     * 2. Validates balance and currency compatibility.
     * 3. Updates Account balance(s) (debit source, credit target on internal transfer).
     * 4. Inserts TRANSACTION record.
     * 5. Synchronously inserts AUDIT_LOG for immediate security non-repudiation.
     * 6. Inserts OUTBOX_EVENT with multi-leg entries[] for Kafka propagation.
     * 7. Registers TransactionSynchronization: complete(key) afterCommit, release(key) on rollback.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public MutationResponse mutateBalance(MutationRequest request, String username) {
        long startTimeNanos = System.nanoTime();
        final String idempotencyKey = request.getIdempotencyKey();

        // 1. Request-shape validation (no locks held yet)
        Long srcId = request.getAccountId();
        Long tgtId = request.getTargetAccountId();
        String operation = request.getOperation().toUpperCase();

        if (srcId == null) {
            throw new AccountNotFoundException("Source account ID cannot be null.");
        }
        if (tgtId != null) {
            if (srcId.equals(tgtId)) {
                throw new InvalidTransferException("Source and target accounts must be different.");
            }
            if (!"DEBIT".equals(operation)) {
                throw new InvalidTransferException(
                    "Transfers must use operation DEBIT (debit source, credit target). " +
                    "A CREDIT cannot include a targetAccountId.");
            }
        }

        // 2. Deadlock-free Ordered Pessimistic Locking
        long lockStart = System.nanoTime();
        Account srcAccount;
        Account tgtAccount = null;

        if (tgtId != null) {
            long firstId = Math.min(srcId, tgtId);
            long secondId = Math.max(srcId, tgtId);

            Account first = accountRepository.findByIdForUpdate(firstId)
                    .orElseThrow(() -> new AccountNotFoundException("Account with ID " + firstId + " was not found."));
            Account second = accountRepository.findByIdForUpdate(secondId)
                    .orElseThrow(() -> new AccountNotFoundException("Account with ID " + secondId + " was not found."));

            srcAccount = (srcId == firstId) ? first : second;
            tgtAccount = (tgtId == firstId) ? first : second;
        } else {
            srcAccount = accountRepository.findByIdForUpdate(srcId)
                    .orElseThrow(() -> new AccountNotFoundException("Account with ID " + srcId + " was not found."));
        }
        telemetryService.recordLockWait(System.nanoTime() - lockStart);

        // 3. Currency Validation
        if (request.getCurrency() != null && !srcAccount.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new CurrencyMismatchException("Account currency (" + srcAccount.getCurrency() + ") does not match transaction currency (" + request.getCurrency() + ").");
        }
        if (tgtAccount != null && request.getCurrency() != null && !tgtAccount.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new CurrencyMismatchException("Target account currency (" + tgtAccount.getCurrency() + ") does not match transaction currency (" + request.getCurrency() + ").");
        }

        BigDecimal beforeBalance = srcAccount.getCurrentBalance();
        BigDecimal mutationAmount = request.getMutationAmount();
        BigDecimal afterBalance;

        List<EntryPayload> legEntries = new ArrayList<>();

        // 4. Overdraft Prevention & State Mutation
        if ("DEBIT".equals(operation)) {
            if (beforeBalance.compareTo(mutationAmount) < 0) {
                throw new InsufficientFundsException("Insufficient account balance. Available: ₱" + beforeBalance.toPlainString() + ", Requested: ₱" + mutationAmount.toPlainString());
            }
            afterBalance = beforeBalance.subtract(mutationAmount);
            srcAccount.setCurrentBalance(afterBalance);
            accountRepository.save(srcAccount);

            legEntries.add(new EntryPayload(srcAccount.getAccountId(), srcAccount.getCustomerId(), "DEBIT", mutationAmount, srcAccount.getCurrency(), beforeBalance, afterBalance));

            // If internal transfer, credit target account
            if (tgtAccount != null) {
                BigDecimal tgtBefore = tgtAccount.getCurrentBalance();
                BigDecimal tgtAfter = tgtBefore.add(mutationAmount);
                tgtAccount.setCurrentBalance(tgtAfter);
                accountRepository.save(tgtAccount);
                legEntries.add(new EntryPayload(tgtAccount.getAccountId(), tgtAccount.getCustomerId(), "CREDIT", mutationAmount, tgtAccount.getCurrency(), tgtBefore, tgtAfter));
            }
        } else if ("CREDIT".equals(operation)) {
            afterBalance = beforeBalance.add(mutationAmount);
            srcAccount.setCurrentBalance(afterBalance);
            accountRepository.save(srcAccount);
            legEntries.add(new EntryPayload(srcAccount.getAccountId(), srcAccount.getCustomerId(), "CREDIT", mutationAmount, srcAccount.getCurrency(), beforeBalance, afterBalance));
        } else {
            throw new IllegalArgumentException("Unsupported mutation operation: " + operation);
        }

        // 5. Insert TRANSACTION Record
        String referenceNo = "TX-PH-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        TransactionRecord txRecord = new TransactionRecord(
                srcAccount.getAccountId(),
                tgtAccount != null ? tgtAccount.getAccountId() : null,
                mutationAmount,
                srcAccount.getCurrency(),
                tgtAccount != null ? tgtAccount.getCurrency() : srcAccount.getCurrency(),
                request.getTransactionType() != null ? request.getTransactionType() : operation,
                referenceNo,
                "SUCCESS",
                null
        );
        txRecord.setOperation(operation);
        txRecord = transactionRepository.save(txRecord);

        // 6. Synchronously Insert AUDIT_LOG in Oracle XE
        AuditLog auditLog = new AuditLog(
                srcAccount.getCustomerId(),
                "BALANCE_MUTATION_" + operation,
                "ACCOUNT",
                String.format("User [%s] executed %s of ₱%s on Account [%s]. Balance: ₱%s -> ₱%s. Ref: %s",
                        username != null ? username : "system", operation, mutationAmount.toPlainString(),
                        srcAccount.getAccountNumber(), beforeBalance.toPlainString(), afterBalance.toPlainString(), referenceNo)
        );
        auditLogRepository.save(auditLog);

        // 7. Insert OUTBOX_EVENT in Oracle XE (shared ledger-common contract with entries[] per leg)
        TransactionOutboxPayload payload = new TransactionOutboxPayload(
                txRecord.getTransactionId(),
                referenceNo,
                srcAccount.getCustomerId(),
                srcAccount.getAccountId(),
                operation,
                txRecord.getTransactionType(),
                mutationAmount,
                srcAccount.getCurrency(),
                beforeBalance,
                afterBalance,
                "SUCCESS",
                legEntries
        );
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            // Never publish a partial event: roll back the whole mutation instead (-> 503 RFC-7807)
            throw new LedgerPersistenceException("Failed to serialize outbox payload for " + referenceNo, ex);
        }

        OutboxEvent outboxEvent = new OutboxEvent(txRecord.getTransactionId(), "TRANSACTION_SUCCESS", payloadJson);
        outboxEventRepository.save(outboxEvent);

        // 8. Build Response
        final MutationResponse response = new MutationResponse(
                txRecord.getTransactionId(),
                referenceNo,
                srcAccount.getAccountId(),
                srcAccount.getAccountNumber(),
                operation,
                mutationAmount,
                srcAccount.getCurrency(),
                beforeBalance,
                afterBalance,
                "SUCCESS",
                false
        );

        // 9. Register Transaction Synchronization for Redis Idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        idempotencyService.complete(idempotencyKey, response);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            idempotencyService.releaseIfInProgress(idempotencyKey);
                        }
                    }
                });
            } else {
                idempotencyService.complete(idempotencyKey, response);
            }
        }

        telemetryService.recordMutation(false, System.nanoTime() - startTimeNanos);
        return response;
    }
}
