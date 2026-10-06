package com.bank.loan.service;

import com.bank.loan.config.LoanProperties;
import com.bank.loan.dto.LoanDtos.EodResult;
import com.bank.loan.exception.LoanException;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanSchedule;
import com.bank.loan.repository.LoanRepository;
import com.bank.loan.repository.LoanScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bank.loan.service.AmortizationCalculator.money;

/**
 * End-of-day loan job. Runs at 23:30 Manila time on each business date, and on demand via POST /loans/eod/run (admin).
 * (There is no EOD Batch Service yet; move this job there later.)
 *
 * 1. Overdue: every PENDING installment due before the business date is marked OVERDUE with a one-time penalty of
 *    2% of the installment (penalty_charged makes it once only); the loan becomes OVERDUE and loan.installment.overdue
 *    is written. Each loan is its own transaction; LOAN is locked before its schedule rows, like repayments.
 * 2. Auto-debit: for every loan with an unpaid installment due on or before the business date, the penalty plus
 *    everything due is collected from the loan's account through the normal repayment path (internal transfer
 *    customer → PH1000000LOAN, key AUTODEBIT-{loanId}-{date}). All or nothing: if the balance is short, nothing is
 *    taken, the loan records INSUFFICIENT_FUNDS (shown to the customer) and loan.autodebit.failed is written once.
 *    An installment that can't be collected on its due date becomes overdue at the next EOD and is retried nightly.
 *    The customer can still pay manually at any time (early, or after a failed debit).
 * Running twice for the same date changes nothing.
 */
@Service
public class LoanEodService {

    private static final Logger log = LoggerFactory.getLogger(LoanEodService.class);

    private final LoanRepository loanRepository;
    private final LoanScheduleRepository scheduleRepository;
    private final LoanRepaymentService repayments;
    private final LoanEvents events;
    private final LoanProperties props;
    private final TransactionTemplate tx;

    public LoanEodService(LoanRepository loanRepository, LoanScheduleRepository scheduleRepository,
                          LoanRepaymentService repayments, LoanEvents events, LoanProperties props, TransactionTemplate tx) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.repayments = repayments;
        this.events = events;
        this.props = props;
        this.tx = tx;
    }

    /** Late in the day, so an installment is collected on its due date and the customer has that day to top up. */
    @Scheduled(cron = "0 30 23 * * *", zone = "Asia/Manila")
    public void scheduledRun() {
        EodResult result = run(events.todayManila());
        log.info("[loan-service] Scheduled EOD {}", result);
    }

    public EodResult run(LocalDate businessDate) {
        int loans = 0, overdue = 0, penalties = 0;
        for (Long loanId : scheduleRepository.findLoanIdsWithPendingDueBefore(businessDate)) {
            int[] counts = tx.execute(status -> markOverdue(loanId, businessDate));
            if (counts != null && counts[0] > 0) {
                loans++;
                overdue += counts[0];
                penalties += counts[1];
            }
        }
        int paid = 0, insufficient = 0, failed = 0;
        for (Long loanId : scheduleRepository.findLoanIdsWithUnpaidDueOnOrBefore(businessDate)) {
            String outcome = autoDebit(loanId, businessDate);
            if (Loan.AUTODEBIT_PAID.equals(outcome)) paid++;
            else if (Loan.AUTODEBIT_INSUFFICIENT_FUNDS.equals(outcome)) insufficient++;
            else if (Loan.AUTODEBIT_FAILED.equals(outcome)) failed++;
        }
        return new EodResult(businessDate, loans, overdue, penalties, paid, insufficient, failed);
    }

    /** Collects the penalty plus everything due on or before the business date. Returns the outcome, or null if nothing was due. */
    String autoDebit(Long loanId, LocalDate businessDate) {
        Loan loan = loanRepository.findById(loanId).orElse(null);
        if (loan == null || Loan.STATUS_CLOSED.equals(loan.getStatus())) return null;
        BigDecimal due = loan.getPenaltyDue().add(scheduleRepository.findByLoanIdOrderByInstallmentNo(loanId).stream()
                .filter(s -> !LoanSchedule.STATUS_PAID.equals(s.getStatus()) && !s.getDueDate().isAfter(businessDate))
                .map(LoanSchedule::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal amount = money(due);
        if (amount.signum() <= 0) return null;

        String outcome;
        try {
            repayments.repay(loan.getCustomerId(), loanId, "AUTODEBIT-" + loanId + "-" + businessDate, amount,
                    "loan-eod-" + businessDate);
            outcome = Loan.AUTODEBIT_PAID;
        } catch (LoanException e) {
            outcome = "insufficient-funds".equals(e.getType()) ? Loan.AUTODEBIT_INSUFFICIENT_FUNDS : Loan.AUTODEBIT_FAILED;
            log.warn("[loan-service] EOD {}: auto-debit of {} for loan {} not collected: {}", businessDate, amount,
                    loan.getReferenceNo(), e.getMessage());
        } catch (RuntimeException e) {
            outcome = Loan.AUTODEBIT_FAILED;
            log.error("[loan-service] EOD {}: auto-debit for loan {} failed: {}", businessDate, loan.getReferenceNo(), e.getMessage());
        }
        String result = outcome;
        tx.executeWithoutResult(status -> recordAutoDebit(loanId, businessDate, amount, result));
        return outcome;
    }

    private void recordAutoDebit(Long loanId, LocalDate businessDate, BigDecimal amount, String outcome) {
        Loan loan = loanRepository.lockById(loanId).orElse(null);
        if (loan == null) return;
        boolean alreadyAlerted = businessDate.equals(loan.getLastAutoDebitDate()) && outcome.equals(loan.getLastAutoDebitStatus());
        loan.setLastAutoDebitDate(businessDate);
        loan.setLastAutoDebitStatus(outcome);
        loan.setLastAutoDebitAmount(amount);
        loanRepository.save(loan);
        if (Loan.AUTODEBIT_INSUFFICIENT_FUNDS.equals(outcome) && !alreadyAlerted) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("customerId", loan.getCustomerId());
            payload.put("accountId", loan.getAccountId());
            payload.put("loanReferenceNo", loan.getReferenceNo());
            payload.put("businessDate", businessDate.toString());
            payload.put("amount", amount);
            events.publish(LoanEvents.AUTODEBIT_FAILED, loan.getReferenceNo(), payload);
        }
    }

    private int[] markOverdue(Long loanId, LocalDate businessDate) {
        Loan loan = loanRepository.lockById(loanId).orElse(null);
        if (loan == null || Loan.STATUS_CLOSED.equals(loan.getStatus())) return new int[]{0, 0};

        List<LoanSchedule> due = scheduleRepository.findByLoanIdOrderByInstallmentNo(loanId).stream()
                .filter(s -> LoanSchedule.STATUS_PENDING.equals(s.getStatus()) && s.getDueDate().isBefore(businessDate))
                .toList();
        int penalties = 0;
        for (LoanSchedule row : due) {
            row.setStatus(LoanSchedule.STATUS_OVERDUE);
            BigDecimal penalty = BigDecimal.ZERO;
            if (!row.isPenaltyCharged()) {
                penalty = money(row.totalDue().multiply(props.getPenaltyRate()));
                loan.setPenaltyDue(loan.getPenaltyDue().add(penalty));
                row.setPenaltyCharged(true);
                penalties++;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("customerId", loan.getCustomerId());
            payload.put("accountId", loan.getAccountId());
            payload.put("loanReferenceNo", loan.getReferenceNo());
            payload.put("installmentNo", row.getInstallmentNo());
            payload.put("dueDate", row.getDueDate().toString());
            payload.put("penalty", penalty);
            events.publish(LoanEvents.INSTALLMENT_OVERDUE, loan.getReferenceNo(), payload);
        }
        if (!due.isEmpty()) {
            loan.setStatus(Loan.STATUS_OVERDUE);
            scheduleRepository.saveAll(due);
            loanRepository.save(loan);
            log.info("[loan-service] EOD {}: loan {} has {} new overdue installment(s), penalty_due={}",
                    businessDate, loan.getReferenceNo(), due.size(), loan.getPenaltyDue());
        }
        return new int[]{due.size(), penalties};
    }
}
