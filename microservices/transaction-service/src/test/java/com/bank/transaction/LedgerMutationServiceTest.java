package com.bank.transaction;

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
import com.bank.transaction.service.LedgerMutationService;
import com.bank.transaction.service.TelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LedgerMutationService.
 * Ports the 3 backend tests (ValidationAndBoundary, Idempotency, PessimisticLock)
 * to the microservice package. All external dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerMutationServiceTest {

    @Mock private AccountRepository       accountRepository;
    @Mock private TransactionRepository   transactionRepository;
    @Mock private OutboxEventRepository   outboxEventRepository;
    @Mock private AuditLogRepository      auditLogRepository;
    @Mock private KafkaTemplate<String,String> kafkaTemplate;
    @Mock private StringRedisTemplate     redisTemplate;
    @Mock private ValueOperations<String,String> valueOps;
    @Mock private TelemetryService        telemetryService;

    @InjectMocks
    private LedgerMutationService service;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private Account account;

    private void sf(Object o,String n,Object v){try{var x=o.getClass().getDeclaredField(n);x.setAccessible(true);x.set(o,v);}catch(Exception e){throw new RuntimeException(e);}}

    @BeforeEach void setUp() throws Exception {
        account = new Account();
        sf(account,"accountId",1L); sf(account,"customerId",10L);
        sf(account,"accountNumber","ACC-001"); sf(account,"currency","PHP");
        sf(account,"currentBalance",new BigDecimal("1000.0000"));
        sf(account,"status","ACTIVE");

        // Inject ObjectMapper (not via @InjectMocks because it is final-constructed)
        var omField = LedgerMutationService.class.getDeclaredField("objectMapper");
        omField.setAccessible(true); omField.set(service, objectMapper);

        // Wire valueOps stub so Redis calls don't NPE
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── Helper: build a saved TransactionRecord stub ─────────────────────────
    private TransactionRecord savedTx(long id) {
        TransactionRecord tx = new TransactionRecord(1L,null,new BigDecimal("100.0000"),"PHP","PHP","DEBIT","REF-"+id,"SUCCESS",null);
        sf(tx,"transactionId",id); return tx;
    }

    // ── CREDIT mutation ──────────────────────────────────────────────────────
    @Test @DisplayName("CREDIT: balance increases by mutation amount")
    void credit_increasesBalance() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(savedTx(1L));
        when(auditLogRepository.save(any())).thenReturn(new AuditLog());
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());
        when(kafkaTemplate.send(any(),any(),any())).thenReturn(null);

        MutationRequest req = new MutationRequest(1L,new BigDecimal("200.0000"),"CREDIT","CREDIT",null,"PHP");
        MutationResponse resp = service.mutateBalance(req,"tester");

        assertThat(resp.getAfterBalance()).isEqualByComparingTo("1200.0000");
        assertThat(resp.getOperation()).isEqualTo("CREDIT");
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
        assertThat(resp.isCachedIdempotentResponse()).isFalse();
    }

    // ── DEBIT mutation ───────────────────────────────────────────────────────
    @Test @DisplayName("DEBIT: balance decreases by mutation amount")
    void debit_decreasesBalance() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(savedTx(2L));
        when(auditLogRepository.save(any())).thenReturn(new AuditLog());
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());
        when(kafkaTemplate.send(any(),any(),any())).thenReturn(null);

        MutationRequest req = new MutationRequest(1L,new BigDecimal("300.0000"),"DEBIT","DEBIT",null,"PHP");
        MutationResponse resp = service.mutateBalance(req,"tester");

        assertThat(resp.getAfterBalance()).isEqualByComparingTo("700.0000");
        assertThat(resp.getOperation()).isEqualTo("DEBIT");
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
    }

    // ── Requirement 1.B: Insufficient funds ──────────────────────────────────
    @Test @DisplayName("DEBIT: amount exceeds balance throws InsufficientFundsException")
    void debit_insufficientFunds_throwsException() {
        sf(account,"currentBalance",new BigDecimal("50.0000"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(savedTx(3L));

        MutationRequest req = new MutationRequest(1L,new BigDecimal("200.0000"),"DEBIT","DEBIT",null,"PHP");
        assertThatThrownBy(()->service.mutateBalance(req,"tester"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient balance");
    }

    // ── Failed DEBIT writes a FAILED transaction record ───────────────────────
    @Test @DisplayName("DEBIT: failed debit still saves a FAILED TransactionRecord")
    void debit_failure_savesFailedRecord() {
        sf(account,"currentBalance",new BigDecimal("10.0000"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        TransactionRecord failedTx = new TransactionRecord(1L,null,new BigDecimal("500.0000"),"PHP","PHP","DEBIT","TX-FAIL-X","FAILED","INSUFFICIENT_FUNDS");
        sf(failedTx,"transactionId",99L);
        when(transactionRepository.save(any())).thenReturn(failedTx);

        MutationRequest req = new MutationRequest(1L,new BigDecimal("500.0000"),"DEBIT","DEBIT",null,"PHP");
        assertThatThrownBy(()->service.mutateBalance(req,"tester"))
                .isInstanceOf(InsufficientFundsException.class);
        verify(transactionRepository,times(1)).save(argThat(tx->tx.getStatus().equals("FAILED")));
    }

    // ── Currency mismatch ────────────────────────────────────────────────────
    @Test @DisplayName("mutate: mismatched currency throws CurrencyMismatchException")
    void mutate_currencyMismatch_throwsException() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        MutationRequest req = new MutationRequest(1L,new BigDecimal("100.0000"),"DEBIT","DEBIT",null,"USD");
        assertThatThrownBy(()->service.mutateBalance(req,"tester"))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch");
    }

    // ── Unknown account ──────────────────────────────────────────────────────
    @Test @DisplayName("mutate: unknown accountId throws AccountNotFoundException")
    void mutate_unknownAccount_throwsException() {
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        MutationRequest req = new MutationRequest(99L,new BigDecimal("100.0000"),"CREDIT","CREDIT",null,"PHP");
        assertThatThrownBy(()->service.mutateBalance(req,"tester"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── Unsupported operation ────────────────────────────────────────────────
    @Test @DisplayName("mutate: unsupported operation throws IllegalArgumentException")
    void mutate_unsupportedOperation_throws() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        MutationRequest req = new MutationRequest(1L,new BigDecimal("50.0000"),"TRANSFER","TRANSFER",null,"PHP");
        assertThatThrownBy(()->service.mutateBalance(req,"tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported operation");
    }

    // ── Requirement 1.D: Redis idempotency ───────────────────────────────────
    @Test @DisplayName("Idempotency: cache hit returns cached response immediately")
    void idempotency_cacheHit_returnsCachedResponse() throws Exception {
        MutationResponse cached = new MutationResponse(1L,"REF-001",1L,"ACC-001","DEBIT",
                new BigDecimal("100.0000"),"PHP",new BigDecimal("1000.0000"),new BigDecimal("900.0000"),"SUCCESS",false);
        String cachedJson = objectMapper.writeValueAsString(cached);
        when(valueOps.get("idempotency:tx:KEY-001")).thenReturn(cachedJson);

        MutationRequest req = new MutationRequest(1L,new BigDecimal("100.0000"),"DEBIT","DEBIT",null,"PHP");
        req.setIdempotencyKey("KEY-001");
        MutationResponse resp = service.mutateBalance(req,"tester");

        assertThat(resp.isCachedIdempotentResponse()).isTrue();
        assertThat(resp.getReferenceNo()).isEqualTo("REF-001");
        // Ensure no DB or Kafka calls made on cache hit
        verify(accountRepository,never()).findByIdForUpdate(anyLong());
        verify(transactionRepository,never()).save(any());
        verify(kafkaTemplate,never()).send(any(),any(),any());
    }

    @Test @DisplayName("Idempotency: no key provided — mutation always executes")
    void idempotency_noKey_alwaysExecutes() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(savedTx(5L));
        when(auditLogRepository.save(any())).thenReturn(new AuditLog());
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());
        when(kafkaTemplate.send(any(),any(),any())).thenReturn(null);
        MutationRequest req = new MutationRequest(1L,new BigDecimal("100.0000"),"CREDIT","CREDIT",null,"PHP");
        // null idempotency key — should not touch Redis at all
        MutationResponse resp = service.mutateBalance(req,"tester");
        assertThat(resp.isCachedIdempotentResponse()).isFalse();
        verify(valueOps,never()).get(anyString());
    }

    // ── Outbox event written inside same transaction ─────────────────────────
    @Test @DisplayName("mutate: OUTBOX_EVENT is saved inside the same transaction")
    void mutate_outboxEventSaved() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(savedTx(6L));
        when(auditLogRepository.save(any())).thenReturn(new AuditLog());
        OutboxEvent outbox = new OutboxEvent(); sf(outbox,"eventId",1L); sf(outbox,"transactionId",6L);
        sf(outbox,"eventType","TRANSACTION_SUCCESS"); sf(outbox,"status","PENDING");
        when(outboxEventRepository.save(any())).thenReturn(outbox);
        when(kafkaTemplate.send(any(),any(),any())).thenReturn(null);
        MutationRequest req = new MutationRequest(1L,new BigDecimal("100.0000"),"CREDIT","CREDIT",null,"PHP");
        service.mutateBalance(req,"tester");
        verify(outboxEventRepository,atLeastOnce()).save(any(OutboxEvent.class));
    }

    // ── Audit log written ────────────────────────────────────────────────────
    @Test @DisplayName("mutate: AUDIT_LOG entry saved for every successful mutation")
    void mutate_auditLogSaved() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenReturn(savedTx(7L));
        when(auditLogRepository.save(any())).thenReturn(new AuditLog());
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());
        when(kafkaTemplate.send(any(),any(),any())).thenReturn(null);
        MutationRequest req = new MutationRequest(1L,new BigDecimal("100.0000"),"DEBIT","DEBIT",null,"PHP");
        service.mutateBalance(req,"tester");
        verify(auditLogRepository,times(1)).save(any(AuditLog.class));
    }

    // ── Requirement 1.B: Concurrency — ported from PessimisticLockConcurrencyTest
    @Test @DisplayName("Concurrency: only 1 of 10 concurrent DEBITs succeeds against insufficient balance")
    void concurrency_pessimisticLock_preventsOverdraft() throws Exception {
        // Each thread gets its own account copy (simulates separate DB rows per lock)
        // We track how many succeed vs are blocked by InsufficientFundsException
        int threads = 10;
        BigDecimal startBalance = new BigDecimal("60.0000");
        BigDecimal debit = new BigDecimal("50.0000");

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger rejections = new AtomicInteger(0);
        Set<String> refs = Collections.synchronizedSet(new HashSet<>());

        // Shared mutable account — balance starts at 60
        Account shared = new Account();
        sf(shared,"accountId",1L); sf(shared,"customerId",10L);
        sf(shared,"accountNumber","ACC-LOCK"); sf(shared,"currency","PHP");
        sf(shared,"currentBalance",startBalance); sf(shared,"status","ACTIVE");

        // accountRepository returns the SAME shared account so balance decrements are visible
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shared));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            TransactionRecord tx = inv.getArgument(0);
            sf(tx,"transactionId",(long)successes.get()+1);
            return tx;
        });
        when(auditLogRepository.save(any())).thenReturn(new AuditLog());
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());
        when(kafkaTemplate.send(any(),any(),any())).thenReturn(null);

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end   = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            exec.submit(() -> {
                try {
                    start.await();
                    MutationRequest req = new MutationRequest(1L,debit,"DEBIT","DEBIT",null,"PHP");
                    MutationResponse r = service.mutateBalance(req,"thread_"+idx);
                    successes.incrementAndGet();
                    refs.add(r.getReferenceNo());
                } catch (InsufficientFundsException ex) {
                    rejections.incrementAndGet();
                } catch (Exception ignored) {}
                finally { end.countDown(); }
            });
        }
        start.countDown();
        assertThat(end.await(10,TimeUnit.SECONDS)).isTrue();
        exec.shutdown();

        // With a 60 starting balance and 50 per debit: exactly 1 succeeds, 9 rejected
        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(9);
        assertThat(refs).hasSize(1);
    }
}
