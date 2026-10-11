package com.bank.loan.service;

import com.bank.loan.client.OrchestratorClient;
import com.bank.loan.config.LoanProperties;
import com.bank.loan.model.*;
import com.bank.loan.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:repayment_recovery;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.jpa.show-sql=false",
    "loan.schema-migration.enabled=false"
}, showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoanRepaymentRecoveryDatabaseTest {
    @Autowired LoanRepository loans;
    @Autowired LoanScheduleRepository schedules;
    @Autowired LoanRepaymentRepository repayments;
    @Autowired OutboxEventRepository outbox;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager entities;
    private LoanRepaymentService service;
    private OrchestratorClient core;
    private LoanEvents events;
    private Long loanId;
    private static final BigDecimal PAYMENT = new BigDecimal("1100.00");

    @BeforeEach void setup() {
        outbox.deleteAll(); repayments.deleteAll(); schedules.deleteAll(); loans.deleteAll();
        // Real pessimistic lock, expressed portably for H2 instead of SQL Server's table hints.
        LoanRepository locking = mock(LoanRepository.class);
        when(locking.lockById(anyLong())).thenAnswer(i -> Optional.ofNullable(
                entities.find(Loan.class, i.getArgument(0, Long.class), LockModeType.PESSIMISTIC_WRITE)));
        when(locking.findById(anyLong())).thenAnswer(i -> loans.findById(i.getArgument(0, Long.class)));
        when(locking.save(any())).thenAnswer(i -> loans.save(i.getArgument(0, Loan.class)));
        var reader = mock(CustomerAccountReader.class);
        when(reader.findAccountById(4L)).thenReturn(Optional.of(
                new CustomerAccountReader.AccountRow(4L,2L,"SOURCE","ACTIVE","PHP")));
        core = mock(OrchestratorClient.class);
        events = spy(new LoanEvents(outbox,new ObjectMapper(),Clock.systemUTC()));
        var query = new LoanQueryService(locking,schedules,reader);
        service = new LoanRepaymentService(locking,schedules,repayments,reader,core,events,
                LoanProperties.defaults(),query,new TransactionTemplate(manager));
        Loan loan = new Loan();
        loan.setReferenceNo("LN-TEST"); loan.setApplicationId(1L); loan.setCustomerId(2L); loan.setAccountId(4L);
        loan.setPrincipal(new BigDecimal("1000")); loan.setOutstandingPrincipal(new BigDecimal("1000"));
        loan.setAnnualRate(new BigDecimal("10")); loan.setTermMonths(1); loan.setMonthlyInstallment(PAYMENT);
        loan.setDisbursedDate(LocalDate.of(2026,10,1)); loan.setMaturityDate(LocalDate.of(2026,11,1));
        loanId=loans.saveAndFlush(loan).getLoanId();
        schedules.saveAndFlush(new LoanSchedule(loanId,1,LocalDate.of(2026,11,1),
                new BigDecimal("1000"),new BigDecimal("100")));
    }

    private void posted() {
        doAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(repayments.findByIdempotencyKey("recover")).isPresent();
            return new OrchestratorClient.TransferResult("POSTED",99L,"FT-TEST",null);
        }).when(core).transfer(any(),any(),any(),any(),any(),any(),any());
    }

    @Test void pendingSurvivesTimeoutAndCompletesWithoutCustomerRetry() {
        when(core.transfer(any(),any(),any(),any(),any(),any(),any()))
                .thenThrow(new IllegalStateException("Lost response after dispatch"));
        assertThatThrownBy(() -> service.repay(2L,loanId,"recover",PAYMENT,null)).isInstanceOf(IllegalStateException.class);
        assertThat(repayments.findByIdempotencyKey("recover").orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(loans.findById(loanId).orElseThrow().getOutstandingPrincipal()).isEqualByComparingTo("1000");
        posted();
        service.recoverRepayments();
        service.recoverRepayments();
        assertAppliedOnce();
        verify(core,times(2)).transfer(eq("SOURCE"),eq("PH1000000LOAN"),argThat(a -> a.compareTo(PAYMENT)==0),eq("LOAN_REPAYMENT"),
                eq("LOAN-REPAY-recover"),any(),any());
    }

    @Test void failedOutboxWriteRollsBackLoanAndCompletionButPreservesCommittedIntent() {
        posted();
        doThrow(new IllegalStateException("Outbox unavailable")).doCallRealMethod()
                .when(events).publish(eq(LoanEvents.REPAYMENT_POSTED),anyString(),anyMap());
        assertThatThrownBy(() -> service.repay(2L,loanId,"recover",PAYMENT,null)).isInstanceOf(IllegalStateException.class);
        assertThat(repayments.findByIdempotencyKey("recover").orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(repayments.findByIdempotencyKey("recover").orElseThrow().getTransactionId()).isNull();
        assertThat(loans.findById(loanId).orElseThrow().getOutstandingPrincipal()).isEqualByComparingTo("1000");
        assertThat(schedules.findByLoanIdOrderByInstallmentNo(loanId).get(0).getAmountPaid()).isEqualByComparingTo("0");
        assertThat(outbox.count()).isZero();
        service.recoverRepayments();
        assertAppliedOnce();
        assertThat(service.repay(2L,loanId,"recover",PAYMENT,null).replayed()).isTrue();
        verify(core,times(2)).transfer(any(),any(),any(),any(),eq("LOAN-REPAY-recover"),any(),any());
    }

    @Test void concurrentCompletionsApplyTheLoanAndEventsOnce() throws Exception {
        when(core.transfer(any(),any(),any(),any(),any(),any(),any()))
                .thenReturn(new OrchestratorClient.TransferResult("PENDING_CORE",null,null,"Timeout"));
        assertThatThrownBy(() -> service.repay(2L,loanId,"recover",PAYMENT,null)).hasMessageContaining("still processing");
        CountDownLatch dispatched = new CountDownLatch(2);
        when(core.transfer(any(),any(),any(),any(),any(),any(),any())).thenAnswer(i -> {
            dispatched.countDown();
            assertThat(dispatched.await(5,TimeUnit.SECONDS)).isTrue();
            return new OrchestratorClient.TransferResult("POSTED",99L,"FT-TEST",null);
        });
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=pool.submit(() -> service.repay(2L,loanId,"recover",PAYMENT,null));
            Future<?> second=pool.submit(service::recoverRepayments);
            first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertAppliedOnce();
    }

    private void assertAppliedOnce() {
        assertThat(repayments.count()).isEqualTo(1);
        assertThat(repayments.findByIdempotencyKey("recover").orElseThrow().getStatus()).isEqualTo("POSTED");
        assertThat(loans.findById(loanId).orElseThrow().getOutstandingPrincipal()).isEqualByComparingTo("0");
        assertThat(loans.findById(loanId).orElseThrow().getStatus()).isEqualTo("CLOSED");
        assertThat(schedules.findByLoanIdOrderByInstallmentNo(loanId).get(0).getAmountPaid()).isEqualByComparingTo(PAYMENT);
        assertThat(outbox.findAll()).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(LoanEvents.REPAYMENT_POSTED,LoanEvents.CLOSED);
    }
}
