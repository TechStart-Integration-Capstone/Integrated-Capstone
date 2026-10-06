package com.bank.loan.service;

import com.bank.loan.client.OrchestratorClient;
import com.bank.loan.config.LoanProperties;
import com.bank.loan.dto.LoanDtos.LoanSummary;
import com.bank.loan.exception.LoanException;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanApplication;
import com.bank.loan.model.LoanSchedule;
import com.bank.loan.repository.CustomerAccountReader;
import com.bank.loan.repository.LoanApplicationRepository;
import com.bank.loan.repository.LoanRepository;
import com.bank.loan.repository.LoanScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /loans/applications/{referenceNo}/accept.
 *
 * One DB transaction: lock the application row (UPDLOCK) → internal transfer PH1000000LOAN → customer
 * → on POSTED insert LOAN + LOAN_SCHEDULE, mark the application ACCEPTED, write loan.disbursed.
 * The row lock is held across the (≤ 3 s) transfer so two concurrent accepts serialize; the second sees
 * ACCEPTED. On REJECTED / PENDING_CORE / timeout the transaction rolls back and nothing is saved; a retry
 * reuses the fixed transfer key LOAN-DISB-{referenceNo}, so money can never move twice.
 */
@Service
public class LoanDisbursementService {

    private static final Logger log = LoggerFactory.getLogger(LoanDisbursementService.class);

    private final LoanApplicationRepository applicationRepository;
    private final LoanRepository loanRepository;
    private final LoanScheduleRepository scheduleRepository;
    private final CustomerAccountReader reader;
    private final OrchestratorClient orchestrator;
    private final LoanEvents events;
    private final LoanProperties props;
    private final LoanQueryService queries;
    private final TransactionTemplate tx;

    public LoanDisbursementService(LoanApplicationRepository applicationRepository, LoanRepository loanRepository,
                                   LoanScheduleRepository scheduleRepository, CustomerAccountReader reader,
                                   OrchestratorClient orchestrator, LoanEvents events, LoanProperties props,
                                   LoanQueryService queries, TransactionTemplate tx) {
        this.applicationRepository = applicationRepository;
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.reader = reader;
        this.orchestrator = orchestrator;
        this.events = events;
        this.props = props;
        this.queries = queries;
        this.tx = tx;
    }

    public LoanSummary accept(Long customerId, String referenceNo, String idempotencyKey, String correlationId) {
        LoanApplicationService.validateKey(idempotencyKey);
        return tx.execute(status -> {
            LoanApplication app = applicationRepository.lockByReferenceNo(referenceNo)
                    .filter(a -> a.getCustomerId().equals(customerId))
                    .orElseThrow(LoanException::loanNotFound);

            if (LoanApplication.STATUS_ACCEPTED.equals(app.getStatus())) throw LoanException.alreadyAccepted();
            if (LoanDecisionEngine.DECLINED.equals(app.getDecision())) throw LoanException.offerDeclined();
            if (LoanApplication.STATUS_EXPIRED.equals(app.getStatus()) || !app.getExpiresAt().isAfter(events.nowUtc())) {
                throw LoanException.offerExpired();
            }

            var account = reader.findAccountById(app.getAccountId()).orElseThrow(LoanException::accountNotOwned);
            OrchestratorClient.TransferResult transfer = orchestrator.transfer(
                    props.getBankAccountNo(), account.accountNumber(), AmortizationCalculator.money(app.getOfferedAmount()),
                    "LOAN_DISBURSEMENT", "LOAN-DISB-" + app.getReferenceNo(), "Loan " + app.getReferenceNo(), correlationId);

            if (!transfer.isPosted()) {
                log.warn("[loan-service] Disbursement for {} not posted: {} {}", referenceNo, transfer.status(), transfer.reason());
                throw LoanException.coreUnavailable(
                        "The loan could not be disbursed right now. Please try again in a moment.");
            }

            Loan loan = createLoan(app, transfer);
            log.info("[loan-service] Loan {} disbursed from application {} txId={} ft={}", loan.getReferenceNo(),
                    referenceNo, transfer.transactionId(), transfer.ftReference());
            return queries.summary(loan, account.accountNumber());
        });
    }

    private Loan createLoan(LoanApplication app, OrchestratorClient.TransferResult transfer) {
        LocalDate disbursed = events.todayManila();
        var rows = AmortizationCalculator.schedule(app.getOfferedAmount(), app.getAnnualRate(), app.getOfferedTerm(), disbursed);

        Loan loan = new Loan();
        loan.setReferenceNo(LoanEvents.placeholderReference());
        loan.setApplicationId(app.getApplicationId());
        loan.setCustomerId(app.getCustomerId());
        loan.setAccountId(app.getAccountId());
        loan.setPrincipal(AmortizationCalculator.money(app.getOfferedAmount()));
        loan.setAnnualRate(app.getAnnualRate());
        loan.setTermMonths(app.getOfferedTerm());
        loan.setMonthlyInstallment(app.getMonthlyInstallment());
        loan.setOutstandingPrincipal(loan.getPrincipal());
        loan.setDisbursementTxnId(transfer.transactionId());
        loan.setFtReference(transfer.ftReference());
        loan.setDisbursedDate(disbursed);
        loan.setMaturityDate(disbursed.plusMonths(app.getOfferedTerm()));
        loan = loanRepository.save(loan);
        loan.setReferenceNo(events.reference("LN", loan.getLoanId()));
        loan = loanRepository.save(loan);

        Long loanId = loan.getLoanId();
        List<LoanSchedule> schedule = rows.stream()
                .map(r -> new LoanSchedule(loanId, r.number(), r.dueDate(), r.principal(), r.interest()))
                .toList();
        scheduleRepository.saveAll(schedule);

        app.setStatus(LoanApplication.STATUS_ACCEPTED);
        applicationRepository.save(app);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", loan.getCustomerId());
        payload.put("accountId", loan.getAccountId());
        payload.put("loanReferenceNo", loan.getReferenceNo());
        payload.put("applicationReferenceNo", app.getReferenceNo());
        payload.put("amount", loan.getPrincipal());
        payload.put("firstDueDate", rows.get(0).dueDate().toString());
        payload.put("transactionId", transfer.transactionId());
        payload.put("ftReference", transfer.ftReference());
        events.publish(LoanEvents.DISBURSED, loan.getReferenceNo(), payload);
        return loan;
    }
}
