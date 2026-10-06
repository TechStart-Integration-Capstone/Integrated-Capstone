package com.bank.loan.service;

import com.bank.loan.config.LoanProperties;
import com.bank.loan.dto.LoanDtos.EodResult;
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
 * End-of-day overdue job. Runs at 00:05 Manila time and on demand via POST /loans/eod/run (admin).
 * (There is no EOD Batch Service yet; move this job there later.)
 *
 * For every PENDING installment due before the business date: mark it OVERDUE, charge a one-time penalty
 * of 2% of the installment (penalty_charged makes it once only), mark the loan OVERDUE and write
 * loan.installment.overdue. Each loan is its own transaction; LOAN is locked before its schedule rows,
 * the same order repayments use. Running twice for the same date changes nothing.
 */
@Service
public class LoanEodService {

    private static final Logger log = LoggerFactory.getLogger(LoanEodService.class);

    private final LoanRepository loanRepository;
    private final LoanScheduleRepository scheduleRepository;
    private final LoanEvents events;
    private final LoanProperties props;
    private final TransactionTemplate tx;

    public LoanEodService(LoanRepository loanRepository, LoanScheduleRepository scheduleRepository, LoanEvents events,
                          LoanProperties props, TransactionTemplate tx) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.events = events;
        this.props = props;
        this.tx = tx;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Manila")
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
        return new EodResult(businessDate, loans, overdue, penalties);
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
