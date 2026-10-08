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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /loans/applications/{referenceNo}/accept.
 *
 * Two DB transactions:
 *   1. Claim — lock the application, check it can be accepted (incl. the credit limit), mark it DISBURSING, commit.
 *   2. Disburse — lock it again (UPDLOCK, held across the transfer so concurrent attempts serialize), internal
 *      transfer PH1000000LOAN → customer, and on POSTED insert LOAN + LOAN_SCHEDULE, mark it ACCEPTED, write
 *      loan.disbursed.
 *
 * The money moves in transaction-service, outside this database transaction. Committing DISBURSING first means a
 * disbursement whose loan was not recorded (orchestrator timeout, PENDING_CORE, a crash after the transfer posted)
 * is never lost: {@link #recoverDisbursements()} retries it with the same transfer key LOAN-DISB-{referenceNo},
 * which the orchestrator replays instead of moving money twice, and records the loan once the transfer is POSTED.
 * A definitive REJECTED marks the application FAILED (nothing was credited; the customer applies again).
 */
@Service
public class LoanDisbursementService {

    private static final Logger log = LoggerFactory.getLogger(LoanDisbursementService.class);

    private final LoanApplicationRepository applicationRepository;
    private final LoanRepository loanRepository;
    private final LoanScheduleRepository scheduleRepository;
    private final CustomerAccountReader reader;
    private final OrchestratorClient orchestrator;
    private final LoanCreditLimitService creditLimit;
    private final LoanEvents events;
    private final LoanProperties props;
    private final LoanQueryService queries;
    private final TransactionTemplate tx;

    public LoanDisbursementService(LoanApplicationRepository applicationRepository, LoanRepository loanRepository,
                                   LoanScheduleRepository scheduleRepository, CustomerAccountReader reader,
                                   OrchestratorClient orchestrator, LoanCreditLimitService creditLimit, LoanEvents events,
                                   LoanProperties props, LoanQueryService queries, TransactionTemplate tx) {
        this.applicationRepository = applicationRepository;
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.reader = reader;
        this.orchestrator = orchestrator;
        this.creditLimit = creditLimit;
        this.events = events;
        this.props = props;
        this.queries = queries;
        this.tx = tx;
    }

    /** Outcome of the disburse transaction; a REJECTED transfer is reported after its FAILED status commits. */
    private record Disbursed(LoanSummary loan, boolean rejected) {}

    public LoanSummary accept(Long customerId, String referenceNo, String idempotencyKey, String correlationId) {
        LoanApplicationService.validateKey(idempotencyKey);
        tx.executeWithoutResult(status -> claim(customerId, referenceNo));
        return disburse(customerId, referenceNo, correlationId);
    }

    private void claim(Long customerId, String referenceNo) {
        LoanApplication app = applicationRepository.lockByReferenceNo(referenceNo)
                .filter(a -> a.getCustomerId().equals(customerId))
                .orElseThrow(LoanException::loanNotFound);

        if (LoanApplication.STATUS_ACCEPTED.equals(app.getStatus())) throw LoanException.alreadyAccepted();
        if (LoanDecisionEngine.DECLINED.equals(app.getDecision())) throw LoanException.offerDeclined();
        if (LoanApplication.STATUS_FAILED.equals(app.getStatus())) throw LoanException.disbursementFailed();
        // A retry of an earlier attempt: money may already have moved, so expiry and the limit no longer apply.
        if (LoanApplication.STATUS_DISBURSING.equals(app.getStatus())) return;
        if (LoanApplication.STATUS_EXPIRED.equals(app.getStatus()) || !app.getExpiresAt().isAfter(events.nowUtc())) {
            throw LoanException.offerExpired();
        }
        creditLimit.checkAccept(app);

        app.setStatus(LoanApplication.STATUS_DISBURSING);
        applicationRepository.save(app);
    }

    public static final int MAX_DISBURSEMENT_ATTEMPTS = 3;

    /** customerId is null when called by the recovery job. */
    private LoanSummary disburse(Long customerId, String referenceNo, String correlationId) {
        Disbursed result = tx.execute(status -> {
            LoanApplication app = applicationRepository.lockByReferenceNo(referenceNo)
                    .filter(a -> customerId == null || a.getCustomerId().equals(customerId))
                    .orElseThrow(LoanException::loanNotFound);
            var account = reader.findAccountById(app.getAccountId()).orElseThrow(LoanException::accountNotOwned);

            if (LoanApplication.STATUS_ACCEPTED.equals(app.getStatus())) {
                // Finished by a concurrent attempt (e.g. the recovery job) while this one waited for the lock.
                Loan loan = loanRepository.findByApplicationId(app.getApplicationId()).orElseThrow(LoanException::alreadyAccepted);
                return new Disbursed(queries.summary(loan, account.accountNumber()), false);
            }
            if (LoanApplication.STATUS_FAILED.equals(app.getStatus())) throw LoanException.disbursementFailed();

            int attempts = app.getRetryCount() + 1;
            app.setRetryCount(attempts);

            OrchestratorClient.TransferResult transfer = orchestrator.transfer(
                    props.getBankAccountNo(), account.accountNumber(), AmortizationCalculator.money(app.getOfferedAmount()),
                    "LOAN_DISBURSEMENT", "LOAN-DISB-" + app.getReferenceNo(), "Loan " + app.getReferenceNo(), correlationId);

            if (transfer.isPosted()) {
                Loan loan = createLoan(app, transfer);
                log.info("[loan-service] Loan {} disbursed from application {} txId={} ft={} (attempt {}/{})", loan.getReferenceNo(),
                        referenceNo, transfer.transactionId(), transfer.ftReference(), attempts, MAX_DISBURSEMENT_ATTEMPTS);
                return new Disbursed(queries.summary(loan, account.accountNumber()), false);
            }
            if ("REJECTED".equals(transfer.status())) {
                log.warn("[loan-service] Disbursement for {} rejected (attempt {}/{}): {}",
                        referenceNo, attempts, MAX_DISBURSEMENT_ATTEMPTS, transfer.reason());
                if (attempts >= MAX_DISBURSEMENT_ATTEMPTS) {
                    log.error("[loan-service] Disbursement for {} reached max attempts ({}), marking FAILED",
                            referenceNo, MAX_DISBURSEMENT_ATTEMPTS);
                    app.setStatus(LoanApplication.STATUS_FAILED);
                    applicationRepository.save(app);
                    return new Disbursed(null, true);
                } else {
                    applicationRepository.save(app);
                    return new Disbursed(null, false);
                }
            }
            // PENDING_CORE: outcome unknown. The application stays DISBURSING; recovery finishes it.
            log.warn("[loan-service] Disbursement for {} not posted yet (attempt {}/{}): {} {}",
                    referenceNo, attempts, MAX_DISBURSEMENT_ATTEMPTS, transfer.status(), transfer.reason());
            if (attempts >= MAX_DISBURSEMENT_ATTEMPTS) {
                log.error("[loan-service] Disbursement for {} reached max attempts ({}) on pending/timeout, marking FAILED",
                        referenceNo, MAX_DISBURSEMENT_ATTEMPTS);
                app.setStatus(LoanApplication.STATUS_FAILED);
                applicationRepository.save(app);
                return new Disbursed(null, true);
            }
            applicationRepository.save(app);
            throw LoanException.coreUnavailable(
                    "Your loan is still being processed. It will appear under My loans shortly — there is no need to accept again.");
        });
        if (result.rejected()) throw LoanException.disbursementFailed();
        if (result.loan() == null) {
            throw LoanException.coreUnavailable(
                    "Your loan is still being processed. It will appear under My loans shortly — there is no need to accept again.");
        }
        return result.loan();
    }

    /**
     * Finishes disbursements left half-done: DISBURSING applications (and DECIDED ones whose transfer already
     * exists in transaction-service). Safe to run any time — the transfer key is fixed, so money moves at most once.
     */
    @Scheduled(fixedDelayString = "${loan.disbursement-recovery-ms:30000}", initialDelayString = "${loan.disbursement-recovery-ms:30000}")
    public void recoverDisbursements() {
        for (LoanApplication app : applicationRepository.findDisbursementsToRecover()) {
            try {
                LoanSummary loan = disburse(null, app.getReferenceNo(), "loan-recovery-" + app.getReferenceNo());
                log.info("[loan-service] Recovery recorded loan {} for application {}", loan.referenceNo(), app.getReferenceNo());
            } catch (LoanException e) {
                log.info("[loan-service] Recovery of {} not finished: {}", app.getReferenceNo(), e.getMessage());
            } catch (RuntimeException e) {
                log.warn("[loan-service] Recovery of {} failed: {}", app.getReferenceNo(), e.getMessage());
            }
        }
    }

    /**
     * Admin recovery: resets a failed or stuck application back to DECIDED status,
     * allowing the customer to review and accept the offer again.
     */
    public void adminResetApplication(String referenceNo) {
        tx.executeWithoutResult(status -> {
            LoanApplication app = applicationRepository.lockByReferenceNo(referenceNo)
                    .orElseThrow(LoanException::loanNotFound);
            if (LoanApplication.STATUS_ACCEPTED.equals(app.getStatus())) {
                throw LoanException.alreadyAccepted();
            }
            app.setStatus(LoanApplication.STATUS_DECIDED);
            app.setRetryCount(0);
            applicationRepository.save(app);
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
