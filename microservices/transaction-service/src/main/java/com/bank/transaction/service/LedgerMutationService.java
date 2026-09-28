package com.bank.transaction.service;

import com.bank.transaction.dto.MutationRequest;
import com.bank.transaction.dto.MutationResponse;
import com.bank.transaction.exception.AccountNotFoundException;
import com.bank.transaction.exception.CurrencyMismatchException;
import com.bank.transaction.exception.InsufficientFundsException;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.AuditLog;
import com.bank.transaction.model.OutboxEvent;
import com.bank.transaction.model.TransactionRecord;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.AuditLogRepository;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class LedgerMutationService {

    private static final Logger log = LoggerFactory.getLogger(LedgerMutationService.class);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:tx:";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final TelemetryService telemetryService;

    @Value("${app.idempotency.ttl-hours:24}")
    private long idempotencyTtlHours;

    public LedgerMutationService(AccountRepository accountRepository,
                                 TransactionRepository transactionRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 AuditLogRepository auditLogRepository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper,
                                 StringRedisTemplate redisTemplate,
                                 TelemetryService telemetryService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.telemetryService = telemetryService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MutationResponse mutateBalance(MutationRequest request, String username) {
        long methodStart = System.nanoTime();

        // 1. Idempotency check — Redis-backed with TTL
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null) {
                try {
                    MutationResponse cached = objectMapper.readValue(cachedJson, MutationResponse.class);
                    cached.setCachedIdempotentResponse(true);
                    log.info("[transaction-service] Idempotent hit for key={}", idempotencyKey);
                    telemetryService.recordMutation(true, System.nanoTime() - methodStart);
                    return cached;
                } catch (Exception e) {
                    log.warn("[transaction-service] Failed to deserialize cached idempotency response for key={}: {}", idempotencyKey, e.getMessage());
                }
            }
        }

        // 2. Pessimistic lock on account row
        long lockStart = System.nanoTime();
        Account account = accountRepository.findByIdForUpdate(request.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with ID " + request.getAccountId() + " not found."));
        telemetryService.recordLockWait(System.nanoTime() - lockStart);

        // 3. Currency validation
        if (request.getCurrency() != null && !account.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new CurrencyMismatchException("Currency mismatch: account=" + account.getCurrency()
                    + ", request=" + request.getCurrency());
        }

        BigDecimal beforeBalance = account.getCurrentBalance();
        BigDecimal amount = request.getMutationAmount();
        String operation = request.getOperation().toUpperCase();
        BigDecimal afterBalance;

        // 4. Balance mutation with overdraft guard
        if ("DEBIT".equals(operation)) {
            if (beforeBalance.compareTo(amount) < 0) {
                String failRef = "TX-FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                transactionRepository.save(new TransactionRecord(
                        account.getAccountId(), request.getTargetAccountId(), amount,
                        account.getCurrency(), account.getCurrency(),
                        request.getTransactionType(), failRef, "FAILED", "INSUFFICIENT_FUNDS"));
                throw new InsufficientFundsException("Insufficient balance. Available: ₱"
                        + beforeBalance + ", Requested: ₱" + amount);
            }
            afterBalance = beforeBalance.subtract(amount);
        } else if ("CREDIT".equals(operation)) {
            afterBalance = beforeBalance.add(amount);
        } else {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }

        // 5. Persist updated balance
        account.setCurrentBalance(afterBalance);
        accountRepository.save(account);

        // 6. Insert TRANSACTION record
        String referenceNo = "TX-PH-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        TransactionRecord tx = transactionRepository.save(new TransactionRecord(
                account.getAccountId(), request.getTargetAccountId(), amount,
                account.getCurrency(), account.getCurrency(),
                request.getTransactionType(), referenceNo, "SUCCESS", null));

        // 7. Insert AUDIT_LOG in Oracle
        auditLogRepository.save(new AuditLog(account.getCustomerId(),
                "BALANCE_MUTATION_" + operation, "ACCOUNT",
                String.format("User [%s] %s ₱%s on Account [%s]. ₱%s → ₱%s. Ref: %s",
                        username, operation, amount, account.getAccountNumber(),
                        beforeBalance, afterBalance, referenceNo)));

        // 8. Insert OUTBOX_EVENT (Transactional Outbox Pattern)
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "transactionId", tx.getTransactionId(),
                    "referenceNo", referenceNo,
                    "accountId", account.getAccountId(),
                    "customerId", account.getCustomerId(),
                    "operation", operation,
                    "amount", amount.toPlainString(),
                    "currency", account.getCurrency(),
                    "beforeBalance", beforeBalance.toPlainString(),
                    "afterBalance", afterBalance.toPlainString(),
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            payload = "{\"transactionId\":" + tx.getTransactionId()
                    + ",\"referenceNo\":\"" + referenceNo + "\"}";
        }
        OutboxEvent outbox = outboxEventRepository.save(
                new OutboxEvent(tx.getTransactionId(), "TRANSACTION_SUCCESS", payload));

        // 9. Build response
        MutationResponse response = new MutationResponse(
                tx.getTransactionId(), referenceNo, account.getAccountId(),
                account.getAccountNumber(), operation, amount,
                account.getCurrency(), beforeBalance, afterBalance, "SUCCESS", false);

        // 10. Cache idempotency key in Redis with TTL
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
                String responseJson = objectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(redisKey, responseJson, Duration.ofHours(idempotencyTtlHours));
                log.info("[transaction-service] Idempotency key cached in Redis: key={} ttl={}h", idempotencyKey, idempotencyTtlHours);
            } catch (Exception e) {
                log.warn("[transaction-service] Failed to cache idempotency key in Redis: {}", e.getMessage());
            }
        }

        // 11. Publish outbox event to Kafka immediately (best-effort, fire-and-forget)
        // The dedicated outbox-publisher service will pick up any events that fail
        // here by polling OUTBOX_EVENT rows with status = 'PENDING' every 5 seconds.
        try {
            kafkaTemplate.send("ledger.transaction.events",
                    String.valueOf(outbox.getTransactionId()), payload);
            outbox.setStatus("PROCESSED");
            outbox.setProcessedDate(LocalDateTime.now());
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            // Leave status as PENDING — outbox-publisher will retry
            log.warn("[transaction-service] Immediate Kafka publish failed for eventId={}, " +
                     "outbox-publisher will retry: {}", outbox.getEventId(), e.getMessage());
        }

        // Record mutation telemetry (non-idempotent path)
        telemetryService.recordMutation(false, System.nanoTime() - methodStart);

        return response;
    }

    // Scheduled fallback poller REMOVED — responsibility moved to the dedicated
    // outbox-publisher microservice (microservices/outbox-publisher).
    // The outbox-publisher polls OUTBOX_EVENT WHERE status='PENDING' every 5 s
    // and retries FAILED rows every 30 s, providing at-least-once delivery.
}
