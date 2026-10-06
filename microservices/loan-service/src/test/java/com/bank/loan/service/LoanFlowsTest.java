package com.bank.loan.service;

import com.bank.loan.client.OrchestratorClient;
import com.bank.loan.client.OrchestratorClient.TransferResult;
import com.bank.loan.config.LoanProperties;
import com.bank.loan.dto.LoanDtos.*;
import com.bank.loan.exception.LoanException;
import com.bank.loan.model.*;
import com.bank.loan.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Apply → accept → repay → EOD against in-memory repositories (Mockito answers) and a mocked orchestrator.
 * Clock: 2026-10-05 11:25 Manila.
 */
class LoanFlowsTest {

    private static final long CUSTOMER = 2L;
    private static final long OTHER_CUSTOMER = 3L;
    private static final long ACCOUNT_ID = 4L;
    private static final String ACCOUNT_NO = "001133218709";

    private final Map<Long, LoanApplication> applications = new LinkedHashMap<>();
    private final Map<Long, Loan> loans = new LinkedHashMap<>();
    private final List<LoanSchedule> schedule = new ArrayList<>();
    private final Map<Long, LoanRepayment> repayments = new LinkedHashMap<>();
    private final List<OutboxEvent> outbox = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(0);
    private int applicationInserts;

    private OrchestratorClient orchestrator;
    private CustomerAccountReader reader;
    private LoanApplicationService applicationService;
    private LoanDisbursementService disbursementService;
    private LoanRepaymentService repaymentService;
    private LoanEodService eodService;
    private LoanQueryService queryService;
    private LoanCreditLimitService creditLimitService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T03:25:00Z"), ZoneOffset.UTC);
        LoanProperties props = LoanProperties.defaults();
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));

        LoanApplicationRepository applicationRepo = mock(LoanApplicationRepository.class);
        when(applicationRepo.save(any())).thenAnswer(inv -> {
            LoanApplication a = inv.getArgument(0);
            if (a.getApplicationId() == null) { a.setApplicationId(ids.incrementAndGet()); applicationInserts++; }
            applications.put(a.getApplicationId(), a);
            return a;
        });
        when(applicationRepo.findByIdempotencyKey(anyString())).thenAnswer(inv -> applications.values().stream()
                .filter(a -> a.getIdempotencyKey().equals(inv.getArgument(0))).findFirst());
        when(applicationRepo.lockByReferenceNo(anyString())).thenAnswer(inv -> applications.values().stream()
                .filter(a -> a.getReferenceNo().equals(inv.getArgument(0))).findFirst());
        when(applicationRepo.findDisbursementsToRecover()).thenAnswer(inv -> applications.values().stream()
                .filter(a -> a.getStatus().equals("DISBURSING")).toList());
        when(applicationRepo.sumDisbursingAmount(anyLong(), anyLong())).thenAnswer(inv -> applications.values().stream()
                .filter(a -> a.getCustomerId().equals(inv.getArgument(0)) && a.getStatus().equals("DISBURSING")
                        && !a.getApplicationId().equals(inv.getArgument(1)))
                .map(LoanApplication::getOfferedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        LoanRepository loanRepo = mock(LoanRepository.class);
        when(loanRepo.save(any())).thenAnswer(inv -> {
            Loan l = inv.getArgument(0);
            if (l.getLoanId() == null) l.setLoanId(ids.incrementAndGet());
            loans.put(l.getLoanId(), l);
            return l;
        });
        when(loanRepo.lockById(anyLong())).thenAnswer(inv -> Optional.ofNullable(loans.get(inv.<Long>getArgument(0))));
        when(loanRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(loans.get(inv.<Long>getArgument(0))));
        when(loanRepo.findByCustomerIdOrderByLoanIdDesc(anyLong())).thenAnswer(inv -> loans.values().stream()
                .filter(l -> l.getCustomerId().equals(inv.getArgument(0))).toList());
        when(loanRepo.existsByCustomerIdAndStatus(anyLong(), anyString())).thenAnswer(inv -> loans.values().stream()
                .anyMatch(l -> l.getCustomerId().equals(inv.getArgument(0)) && l.getStatus().equals(inv.getArgument(1))));
        when(loanRepo.findByApplicationId(anyLong())).thenAnswer(inv -> loans.values().stream()
                .filter(l -> l.getApplicationId().equals(inv.getArgument(0))).findFirst());
        when(loanRepo.sumOpenPrincipal(anyLong())).thenAnswer(inv -> loans.values().stream()
                .filter(l -> l.getCustomerId().equals(inv.getArgument(0)) && !l.getStatus().equals("CLOSED"))
                .map(Loan::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add));

        LoanScheduleRepository scheduleRepo = mock(LoanScheduleRepository.class);
        when(scheduleRepo.saveAll(anyList())).thenAnswer(inv -> {
            for (LoanSchedule s : inv.<List<LoanSchedule>>getArgument(0)) if (!schedule.contains(s)) schedule.add(s);
            return inv.getArgument(0);
        });
        when(scheduleRepo.findByLoanIdOrderByInstallmentNo(anyLong())).thenAnswer(inv -> schedule.stream()
                .filter(s -> s.getLoanId().equals(inv.getArgument(0)))
                .sorted(Comparator.comparing(LoanSchedule::getInstallmentNo)).toList());
        when(scheduleRepo.findLoanIdsWithPendingDueBefore(any())).thenAnswer(inv -> schedule.stream()
                .filter(s -> s.getStatus().equals("PENDING") && s.getDueDate().isBefore(inv.getArgument(0)))
                .map(LoanSchedule::getLoanId).distinct().toList());

        LoanRepaymentRepository repaymentRepo = mock(LoanRepaymentRepository.class);
        when(repaymentRepo.save(any())).thenAnswer(inv -> {
            LoanRepayment r = inv.getArgument(0);
            if (r.getRepaymentId() == null) r.setRepaymentId(ids.incrementAndGet());
            repayments.put(r.getRepaymentId(), r);
            return r;
        });
        when(repaymentRepo.findByIdempotencyKey(anyString())).thenAnswer(inv -> repayments.values().stream()
                .filter(r -> r.getIdempotencyKey().equals(inv.getArgument(0))).findFirst());

        OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
        when(outboxRepo.save(any())).thenAnswer(inv -> { outbox.add(inv.getArgument(0)); return inv.getArgument(0); });

        reader = mock(CustomerAccountReader.class);
        var account = new CustomerAccountReader.AccountRow(ACCOUNT_ID, CUSTOMER, ACCOUNT_NO, "ACTIVE", "PHP");
        when(reader.findAccountByNumber(ACCOUNT_NO)).thenReturn(Optional.of(account));
        when(reader.findAccountById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        // arosales: NORMAL band
        when(reader.findCustomer(CUSTOMER)).thenReturn(Optional.of(new CustomerAccountReader.CustomerRow(CUSTOMER, 670, new BigDecimal("45000"))));

        orchestrator = mock(OrchestratorClient.class);

        LoanEvents events = new LoanEvents(outboxRepo, new ObjectMapper(), clock);
        LoanDecisionEngine engine = new LoanDecisionEngine(props);
        queryService = new LoanQueryService(loanRepo, scheduleRepo, reader);
        creditLimitService = new LoanCreditLimitService(loanRepo, applicationRepo, reader, engine, props);
        applicationService = new LoanApplicationService(applicationRepo, loanRepo, reader, engine, creditLimitService,
                events, props, tx);
        disbursementService = new LoanDisbursementService(applicationRepo, loanRepo, scheduleRepo, reader, orchestrator,
                creditLimitService, events, props, queryService, tx);
        repaymentService = new LoanRepaymentService(loanRepo, scheduleRepo, repaymentRepo, reader, orchestrator,
                events, props, queryService, tx);
        eodService = new LoanEodService(loanRepo, scheduleRepo, events, props, tx);
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private ApplicationResponse applyNormal(String key) {
        return applicationService.apply(CUSTOMER, key, new ApplyRequest(ACCOUNT_NO, bd("250000.00"), 36)).response();
    }

    private void stubDisbursement(TransferResult result) {
        when(orchestrator.transfer(eq("PH1000000LOAN"), eq(ACCOUNT_NO), any(), eq("LOAN_DISBURSEMENT"), anyString(), anyString(), any()))
                .thenReturn(result);
    }

    private LoanSummary disbursedNormalLoan() {
        ApplicationResponse app = applyNormal("apply-key-1");
        stubDisbursement(new TransferResult("POSTED", 501L, "FT26278ABC12", null));
        return disbursementService.accept(CUSTOMER, app.referenceNo(), "accept-key-1", "corr");
    }

    private List<String> outboxTypes() {
        return outbox.stream().map(OutboxEvent::getEventType).toList();
    }

    // ── Apply ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Apply returns the decision immediately and writes loan.application.decided")
    void apply_savesDecisionAndOutbox() {
        ApplicationResponse response = applyNormal("apply-key-1");

        assertThat(response.referenceNo()).isEqualTo("LAP-20261005-000001");
        assertThat(response.decision()).isEqualTo("APPROVED");
        assertThat(response.creditScore()).isEqualTo(670);
        assertThat(response.band()).isEqualTo("NORMAL");
        assertThat(response.offer().monthlyInstallment()).isEqualByComparingTo("9038.10");
        assertThat(response.offer().annualRate()).isEqualByComparingTo("18.0");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-10-12T03:25:00Z"));
        assertThat(outboxTypes()).containsExactly("loan.application.decided");
        assertThat(outbox.get(0).getAggregateId()).isEqualTo("LAP-20261005-000001");
        assertThat(outbox.get(0).getTransactionId()).isNull();
        assertThat(outbox.get(0).getPayload()).contains("\"eventType\":\"loan.application.decided\"");
    }

    @Test
    @DisplayName("TC-LD-06: same Idempotency-Key twice → same application, one row saved")
    void tcLd06_idempotentApply() {
        ApplicationResponse first = applyNormal("apply-key-1");
        var second = applicationService.apply(CUSTOMER, "apply-key-1", new ApplyRequest(ACCOUNT_NO, bd("250000.00"), 36));

        assertThat(second.replayed()).isTrue();
        assertThat(second.response().referenceNo()).isEqualTo(first.referenceNo());
        assertThat(applicationInserts).isEqualTo(1);
        assertThat(outbox).hasSize(1);
    }

    @Test
    @DisplayName("Apply validates amount/term and account ownership")
    void apply_validation() {
        assertThatThrownBy(() -> applicationService.apply(CUSTOMER, "k1", new ApplyRequest(ACCOUNT_NO, bd("4999.99"), 12)))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("validation-error"));
        assertThatThrownBy(() -> applicationService.apply(CUSTOMER, "k2", new ApplyRequest(ACCOUNT_NO, bd("10000"), 61)))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("validation-error"));
        assertThatThrownBy(() -> applicationService.apply(OTHER_CUSTOMER, "k3", new ApplyRequest(ACCOUNT_NO, bd("10000"), 12)))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("account-not-owned"));
        assertThat(applications).isEmpty();
    }

    // ── Accept / disburse ───────────────────────────────────────────────────

    @Test
    @DisplayName("TC-LN-01: accept → disbursed from PH1000000LOAN, LOAN + 36 schedule rows, ft_reference saved")
    void tcLn01_acceptDisburses() {
        LoanSummary loan = disbursedNormalLoan();

        verify(orchestrator).transfer("PH1000000LOAN", ACCOUNT_NO, bd("250000.00"), "LOAN_DISBURSEMENT",
                "LOAN-DISB-LAP-20261005-000001", "Loan LAP-20261005-000001", "corr");
        assertThat(loan.referenceNo()).isEqualTo("LN-20261005-000002");
        assertThat(loan.outstandingPrincipal()).isEqualByComparingTo("250000.00");
        assertThat(loan.ftReference()).isEqualTo("FT26278ABC12");
        assertThat(loan.nextDue().dueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(loan.nextDue().amount()).isEqualByComparingTo("9038.10");
        assertThat(loans.values()).singleElement().satisfies(l -> {
            assertThat(l.getDisbursementTxnId()).isEqualTo(501L);
            assertThat(l.getMaturityDate()).isEqualTo(LocalDate.of(2029, 10, 5));
        });
        assertThat(schedule).hasSize(36);
        assertThat(applications.values()).singleElement().extracting(LoanApplication::getStatus).isEqualTo("ACCEPTED");
        assertThat(outboxTypes()).containsExactly("loan.application.decided", "loan.disbursed");
        assertThat(outbox.get(1).getPayload()).contains("\"firstDueDate\":\"2026-11-05\"");
    }

    @Test
    @DisplayName("TC-LN-02: accept twice → second is 409 already-accepted; money moved once")
    void tcLn02_acceptTwice() {
        disbursedNormalLoan();
        String ref = applications.values().iterator().next().getReferenceNo();

        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, ref, "accept-key-2", "corr"))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("already-accepted"));
        verify(orchestrator, times(1)).transfer(any(), any(), any(), any(), any(), any(), any());
        assertThat(loans).hasSize(1);
    }

    @Test
    @DisplayName("TC-LN-03: core down → 503, no LOAN saved, offer stays DISBURSING; retry after restart succeeds once")
    void tcLn03_coreDownThenRetry() {
        ApplicationResponse app = applyNormal("apply-key-1");
        stubDisbursement(new TransferResult("PENDING_CORE", null, null, "Orchestrator timeout or unavailable"));

        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, app.referenceNo(), "accept-key-1", "corr"))
                .isInstanceOfSatisfying(LoanException.class, e -> {
                    assertThat(e.getType()).isEqualTo("core-unavailable");
                    assertThat(e.getStatus().value()).isEqualTo(503);
                });
        assertThat(loans).isEmpty();
        assertThat(schedule).isEmpty();
        assertThat(applications.get(1L).getStatus()).isEqualTo("DISBURSING");

        stubDisbursement(new TransferResult("POSTED", 777L, "FT26278RETRY", null));
        disbursementService.accept(CUSTOMER, app.referenceNo(), "accept-key-1", "corr");

        assertThat(loans).hasSize(1);
        // Both attempts used the same fixed transfer key, so the orchestrator can never move money twice.
        verify(orchestrator, times(2)).transfer(any(), any(), any(), any(), eq("LOAN-DISB-LAP-20261005-000001"), any(), any());
    }

    @Test
    @DisplayName("Accept: declined → 409 offer-declined, expired → 409 offer-expired, other customer → 404")
    void accept_conflicts() {
        when(reader.findCustomer(CUSTOMER)).thenReturn(Optional.of(new CustomerAccountReader.CustomerRow(CUSTOMER, 480, bd("45000"))));
        ApplicationResponse declined = applicationService.apply(CUSTOMER, "d1", new ApplyRequest(ACCOUNT_NO, bd("10000"), 12)).response();
        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, declined.referenceNo(), "a1", null))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("offer-declined"));

        when(reader.findCustomer(CUSTOMER)).thenReturn(Optional.of(new CustomerAccountReader.CustomerRow(CUSTOMER, 670, bd("45000"))));
        ApplicationResponse stale = applyNormal("d2");
        applications.values().stream().filter(a -> a.getReferenceNo().equals(stale.referenceNo())).findFirst().orElseThrow()
                .setExpiresAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, stale.referenceNo(), "a2", null))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("offer-expired"));

        assertThatThrownBy(() -> disbursementService.accept(OTHER_CUSTOMER, stale.referenceNo(), "a3", null))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("loan-not-found"));
        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("Disbursement still pending → recovery job records LOAN + schedule once the transfer posts")
    void pendingDisbursement_recoveredByJob() {
        ApplicationResponse app = applyNormal("apply-key-1");
        stubDisbursement(new TransferResult("PENDING_CORE", null, null, "Orchestrator timeout or unavailable"));
        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, app.referenceNo(), "accept-key-1", "corr"))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("core-unavailable"));

        // The orchestrator finished the transfer after the client gave up; a retry with the same key replays it.
        stubDisbursement(new TransferResult("POSTED", 777L, "FT26278LATE", null));
        disbursementService.recoverDisbursements();

        assertThat(loans.values()).singleElement().satisfies(l -> {
            assertThat(l.getDisbursementTxnId()).isEqualTo(777L);
            assertThat(l.getFtReference()).isEqualTo("FT26278LATE");
        });
        assertThat(schedule).hasSize(36);
        assertThat(applications.get(1L).getStatus()).isEqualTo("ACCEPTED");
        assertThat(outboxTypes()).containsExactly("loan.application.decided", "loan.disbursed");
        verify(orchestrator, times(2)).transfer(any(), any(), any(), any(), eq("LOAN-DISB-LAP-20261005-000001"), any(), any());

        // Nothing left to recover; accepting again is a conflict, not a second disbursement.
        disbursementService.recoverDisbursements();
        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, app.referenceNo(), "accept-key-2", "corr"))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("already-accepted"));
        verify(orchestrator, times(2)).transfer(any(), any(), any(), any(), any(), any(), any());
        assertThat(loans).hasSize(1);
    }

    @Test
    @DisplayName("Disbursement rejected → 422 disbursement-failed, application FAILED, never retried")
    void rejectedDisbursement_marksFailed() {
        ApplicationResponse app = applyNormal("apply-key-1");
        stubDisbursement(new TransferResult("REJECTED", null, null, "Core banking T24 rejected transfer"));

        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, app.referenceNo(), "accept-key-1", "corr"))
                .isInstanceOfSatisfying(LoanException.class, e -> {
                    assertThat(e.getType()).isEqualTo("disbursement-failed");
                    assertThat(e.getStatus().value()).isEqualTo(422);
                });
        assertThat(applications.get(1L).getStatus()).isEqualTo("FAILED");
        assertThat(loans).isEmpty();

        disbursementService.recoverDisbursements();
        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, app.referenceNo(), "accept-key-2", "corr"))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("disbursement-failed"));
        verify(orchestrator, times(1)).transfer(any(), any(), any(), any(), any(), any(), any());
    }

    // ── Credit limit ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Credit limit: NORMAL limit fully used by an open loan → next application DECLINED CREDIT_LIMIT_REACHED")
    void creditLimit_openLoanUsesLimit() {
        disbursedNormalLoan(); // 250,000 = the whole NORMAL limit

        ApplicationResponse next = applicationService.apply(CUSTOMER, "apply-key-2", new ApplyRequest(ACCOUNT_NO, bd("10000"), 12)).response();

        assertThat(next.decision()).isEqualTo("DECLINED");
        assertThat(next.declineReason()).isEqualTo("CREDIT_LIMIT_REACHED");
        assertThat(creditLimitService.eligibility(CUSTOMER)).satisfies(e -> {
            assertThat(e.eligible()).isFalse();
            assertThat(e.creditLimit()).isEqualByComparingTo("250000.00");
            assertThat(e.outstanding()).isEqualByComparingTo("250000.00");
            assertThat(e.available()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    @DisplayName("Credit limit: partly used → offer capped at what is left (COUNTER_OFFER)")
    void creditLimit_capsOffer() {
        ApplicationResponse first = applicationService.apply(CUSTOMER, "apply-key-1", new ApplyRequest(ACCOUNT_NO, bd("100000"), 24)).response();
        stubDisbursement(new TransferResult("POSTED", 501L, "FT26278ABC12", null));
        disbursementService.accept(CUSTOMER, first.referenceNo(), "accept-key-1", null);
        assertThat(creditLimitService.eligibility(CUSTOMER).available()).isEqualByComparingTo("150000.00");

        ApplicationResponse second = applicationService.apply(CUSTOMER, "apply-key-2", new ApplyRequest(ACCOUNT_NO, bd("200000"), 24)).response();

        assertThat(second.decision()).isEqualTo("COUNTER_OFFER");
        assertThat(second.offer().amount()).isEqualByComparingTo("150000.00");
    }

    @Test
    @DisplayName("Credit limit: two offers taken out side by side → accepting the second is 409 credit-limit-reached")
    void creditLimit_checkedAgainOnAccept() {
        ApplicationResponse a = applicationService.apply(CUSTOMER, "apply-key-1", new ApplyRequest(ACCOUNT_NO, bd("150000"), 24)).response();
        ApplicationResponse b = applicationService.apply(CUSTOMER, "apply-key-2", new ApplyRequest(ACCOUNT_NO, bd("150000"), 24)).response();
        assertThat(b.decision()).isEqualTo("APPROVED"); // nothing borrowed yet when b was decided
        stubDisbursement(new TransferResult("POSTED", 501L, "FT26278ABC12", null));
        disbursementService.accept(CUSTOMER, a.referenceNo(), "accept-key-1", null);

        assertThatThrownBy(() -> disbursementService.accept(CUSTOMER, b.referenceNo(), "accept-key-2", null))
                .isInstanceOfSatisfying(LoanException.class, e -> {
                    assertThat(e.getType()).isEqualTo("credit-limit-reached");
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });
        verify(orchestrator, times(1)).transfer(any(), any(), any(), any(), any(), any(), any());
        assertThat(loans).hasSize(1);
    }

    // ── Repay ───────────────────────────────────────────────────────────────

    private void stubRepayment(TransferResult result) {
        when(orchestrator.transfer(eq(ACCOUNT_NO), eq("PH1000000LOAN"), any(), eq("LOAN_REPAYMENT"), anyString(), anyString(), any()))
                .thenReturn(result);
    }

    @Test
    @DisplayName("TC-LN-04: repay one installment → row 1 PAID, outstanding reduced by its principal")
    void tcLn04_repayOneInstallment() {
        LoanSummary loan = disbursedNormalLoan();
        stubRepayment(new TransferResult("POSTED", 601L, "FT26278PAY01", null));

        var outcome = repaymentService.repay(CUSTOMER, loan.loanId(), "repay-key-1", bd("9038.10"), "corr");

        verify(orchestrator).transfer(ACCOUNT_NO, "PH1000000LOAN", bd("9038.10"), "LOAN_REPAYMENT",
                "LOAN-REPAY-repay-key-1", "Repayment for loan LN-20261005-000002", "corr");
        LoanSchedule row1 = schedule.get(0);
        assertThat(row1.getStatus()).isEqualTo("PAID");
        assertThat(schedule.get(1).getStatus()).isEqualTo("PENDING");
        // Row 1: interest 3,750.00 (250,000 × 1.5%), principal 5,288.10
        assertThat(outcome.response().outstandingPrincipal()).isEqualByComparingTo("244711.90");
        assertThat(outcome.response().referenceNo()).startsWith("LRP-20261005-");
        assertThat(outcome.response().loanStatus()).isEqualTo("ACTIVE");
        assertThat(outboxTypes()).endsWith("loan.repayment.posted");

        // Same Idempotency-Key again: replayed, money not moved again.
        var replay = repaymentService.repay(CUSTOMER, loan.loanId(), "repay-key-1", bd("9038.10"), "corr");
        assertThat(replay.replayed()).isTrue();
        assertThat(repayments).hasSize(1);
        verify(orchestrator, times(1)).transfer(eq(ACCOUNT_NO), any(), any(), eq("LOAN_REPAYMENT"), any(), any(), any());
    }

    @Test
    @DisplayName("TC-LN-05: repay with insufficient savings → 422, nothing changed")
    void tcLn05_insufficientFunds() {
        LoanSummary loan = disbursedNormalLoan();
        int eventsBefore = outbox.size();
        stubRepayment(new TransferResult("REJECTED", null, null, "INSUFFICIENT_FUNDS"));

        assertThatThrownBy(() -> repaymentService.repay(CUSTOMER, loan.loanId(), "repay-key-1", bd("9038.10"), "corr"))
                .isInstanceOfSatisfying(LoanException.class, e -> {
                    assertThat(e.getType()).isEqualTo("insufficient-funds");
                    assertThat(e.getStatus().value()).isEqualTo(422);
                });
        assertThat(repayments).isEmpty();
        assertThat(schedule).allSatisfy(s -> assertThat(s.getAmountPaid()).isEqualByComparingTo("0"));
        assertThat(loans.get(loan.loanId()).getOutstandingPrincipal()).isEqualByComparingTo("250000.00");
        assertThat(outbox).hasSize(eventsBefore);
    }

    @Test
    @DisplayName("Another customer's loan is reported as not found")
    void otherCustomersLoan_notFound() {
        LoanSummary loan = disbursedNormalLoan();

        assertThatThrownBy(() -> queryService.schedule(OTHER_CUSTOMER, loan.loanId()))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("loan-not-found"));
        assertThatThrownBy(() -> repaymentService.repay(OTHER_CUSTOMER, loan.loanId(), "x", bd("100"), null))
                .isInstanceOfSatisfying(LoanException.class, e -> assertThat(e.getType()).isEqualTo("loan-not-found"));
        assertThat(queryService.myLoans(OTHER_CUSTOMER)).isEmpty();
    }

    // ── EOD ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-EOD-01: EOD after the 1st due date → row OVERDUE, 2% penalty once, loan OVERDUE")
    void tcEod01_overdueAndPenaltyOnce() {
        LoanSummary loan = disbursedNormalLoan();
        LocalDate businessDate = LocalDate.of(2026, 11, 6);

        EodResult first = eodService.run(businessDate);

        assertThat(first.installmentsMarkedOverdue()).isEqualTo(1);
        assertThat(first.penaltiesCharged()).isEqualTo(1);
        assertThat(schedule.get(0).getStatus()).isEqualTo("OVERDUE");
        assertThat(schedule.get(0).isPenaltyCharged()).isTrue();
        assertThat(schedule.get(1).getStatus()).isEqualTo("PENDING");
        Loan stored = loans.get(loan.loanId());
        assertThat(stored.getStatus()).isEqualTo("OVERDUE");
        assertThat(stored.getPenaltyDue()).isEqualByComparingTo("180.76"); // 2% of 9,038.10
        assertThat(outboxTypes()).endsWith("loan.installment.overdue");

        int eventsAfterFirst = outbox.size();
        EodResult second = eodService.run(businessDate);

        assertThat(second.installmentsMarkedOverdue()).isZero();
        assertThat(stored.getPenaltyDue()).isEqualByComparingTo("180.76");
        assertThat(outbox).hasSize(eventsAfterFirst);

        // An OVERDUE loan blocks new applications.
        ApplicationResponse blocked = applicationService.apply(CUSTOMER, "apply-key-2", new ApplyRequest(ACCOUNT_NO, bd("10000"), 12)).response();
        assertThat(blocked.decision()).isEqualTo("DECLINED");
        assertThat(blocked.declineReason()).isEqualTo("EXISTING_LOAN_OVERDUE");
    }

    @Test
    @DisplayName("Paying penalty + overdue installment brings the loan back to ACTIVE")
    void repayAfterOverdue_penaltyFirstThenActive() {
        LoanSummary loan = disbursedNormalLoan();
        eodService.run(LocalDate.of(2026, 11, 6));
        stubRepayment(new TransferResult("POSTED", 602L, "FT26278PAY02", null));

        var outcome = repaymentService.repay(CUSTOMER, loan.loanId(), "repay-key-2", bd("9218.86"), null); // 180.76 + 9,038.10

        assertThat(outcome.response().penaltyDue()).isEqualByComparingTo("0.00");
        assertThat(outcome.response().loanStatus()).isEqualTo("ACTIVE");
        assertThat(schedule.get(0).getStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Paying everything off closes the loan and writes loan.closed")
    void payOff_closesLoan() {
        when(reader.findCustomer(CUSTOMER)).thenReturn(Optional.of(new CustomerAccountReader.CustomerRow(CUSTOMER, 670, bd("45000"))));
        ApplicationResponse app = applicationService.apply(CUSTOMER, "small", new ApplyRequest(ACCOUNT_NO, bd("5000"), 3)).response();
        stubDisbursement(new TransferResult("POSTED", 900L, "FT26278SMALL", null));
        LoanSummary loan = disbursementService.accept(CUSTOMER, app.referenceNo(), "acc", null);
        stubRepayment(new TransferResult("POSTED", 901L, "FT26278PAYOFF", null));

        BigDecimal total = schedule.stream().map(LoanSchedule::totalDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        var outcome = repaymentService.repay(CUSTOMER, loan.loanId(), "payoff", total, null);

        assertThat(outcome.response().loanStatus()).isEqualTo("CLOSED");
        assertThat(outcome.response().outstandingPrincipal()).isEqualByComparingTo("0.00");
        assertThat(outboxTypes()).endsWith("loan.repayment.posted", "loan.closed");
        assertThatThrownBy(() -> repaymentService.repay(CUSTOMER, loan.loanId(), "after", bd("1"), null))
                .isInstanceOf(LoanException.class);
    }
}
