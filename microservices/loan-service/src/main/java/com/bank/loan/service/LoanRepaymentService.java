package com.bank.loan.service;

import com.bank.loan.client.OrchestratorClient;
import com.bank.loan.config.LoanProperties;
import com.bank.loan.dto.LoanDtos.RepaymentResponse;
import com.bank.loan.exception.LoanException;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanRepayment;
import com.bank.loan.model.LoanSchedule;
import com.bank.loan.repository.CustomerAccountReader;
import com.bank.loan.repository.LoanRepaymentRepository;
import com.bank.loan.repository.LoanRepository;
import com.bank.loan.repository.LoanScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bank.loan.service.AmortizationCalculator.money;

/**
 * POST /loans/{loanId}/repayments.
 *
 * Commit a pending repayment before dispatching the internal transfer. Recovery reuses its immutable
 * key and amount. A second short transaction applies a confirmed posting to the loan and outbox once.
 */
@Service
public class LoanRepaymentService {

    private static final Logger log = LoggerFactory.getLogger(LoanRepaymentService.class);

    private final LoanRepository loanRepository;
    private final LoanScheduleRepository scheduleRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final CustomerAccountReader reader;
    private final OrchestratorClient orchestrator;
    private final LoanEvents events;
    private final LoanProperties props;
    private final LoanQueryService queries;
    private final TransactionTemplate tx;

    public LoanRepaymentService(LoanRepository loanRepository, LoanScheduleRepository scheduleRepository,
                                LoanRepaymentRepository repaymentRepository, CustomerAccountReader reader,
                                OrchestratorClient orchestrator, LoanEvents events, LoanProperties props,
                                LoanQueryService queries, TransactionTemplate tx) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.repaymentRepository = repaymentRepository;
        this.reader = reader;
        this.orchestrator = orchestrator;
        this.events = events;
        this.props = props;
        this.queries = queries;
        this.tx = tx;
    }

    public record RepayOutcome(RepaymentResponse response, boolean replayed) {}

    public RepayOutcome repay(Long customerId, Long loanId, String idempotencyKey, BigDecimal amount, String correlationId) {
        LoanApplicationService.validateKey(idempotencyKey);
        BigDecimal payment = money(amount);

        if (payment.signum() <= 0) throw LoanException.validation("amount must be positive.");

        boolean replayed = Boolean.TRUE.equals(tx.execute(status -> {
            Loan loan = loanRepository.lockById(loanId)
                    .filter(l -> l.getCustomerId().equals(customerId))
                    .orElseThrow(LoanException::loanNotFound);

            // A concurrent request with the same key may have finished while we waited for the lock.
            var raced = repaymentRepository.findByIdempotencyKey(idempotencyKey);
            if (raced.isPresent()) {
                replay(raced.get(), customerId, loanId, payment); // Validate ownership and immutable details.
                return true;
            }

            if (Loan.STATUS_CLOSED.equals(loan.getStatus())) throw LoanException.validation("This loan is already fully paid.");
            if (repaymentRepository.existsByLoanIdAndStatus(loanId, LoanRepayment.PENDING)) throw pending();

            List<LoanSchedule> rows = scheduleRepository.findByLoanIdOrderByInstallmentNo(loanId);
            BigDecimal owed = loan.getPenaltyDue().add(rows.stream()
                    .filter(r -> !LoanSchedule.STATUS_PAID.equals(r.getStatus()))
                    .map(LoanSchedule::remaining)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            if (payment.compareTo(owed) > 0) {
                throw LoanException.validation("amount is more than the total still owed (" + money(owed).toPlainString() + ").");
            }

            LoanRepayment repayment = new LoanRepayment();
            repayment.setLoanId(loanId);
            repayment.setReferenceNo(LoanEvents.placeholderReference());
            repayment.setIdempotencyKey(idempotencyKey);
            repayment.setAmount(payment);
            repayment.setStatus(LoanRepayment.PENDING);
            repayment.setCreatedDate(events.nowUtc());
            repayment = repaymentRepository.save(repayment);
            repayment.setReferenceNo(events.reference("LRP", repayment.getRepaymentId()));
            repayment = repaymentRepository.save(repayment);
            return false;
        }));
        return settle(idempotencyKey, correlationId, replayed);
    }

    private RepayOutcome settle(String key, String correlationId, boolean replayed) {
        LoanRepayment intent = repaymentRepository.findByIdempotencyKey(key).orElseThrow();
        Loan snapshot = loanRepository.findById(intent.getLoanId()).orElseThrow(LoanException::loanNotFound);
        if (LoanRepayment.POSTED.equals(intent.getStatus())) return new RepayOutcome(response(intent, snapshot), true);
        if (!LoanRepayment.PENDING.equals(intent.getStatus())) throw terminalFailure(intent);

        var account = reader.findAccountById(snapshot.getAccountId()).orElseThrow(LoanException::loanNotFound);
        // No database transaction/lock is held while the remote service processes the transfer.
        var transfer = orchestrator.transfer(account.accountNumber(), props.getBankAccountNo(), intent.getAmount(),
                "LOAN_REPAYMENT", "LOAN-REPAY-" + key, "Repayment for loan " + snapshot.getReferenceNo(), correlationId);

        RepayOutcome result = tx.execute(status -> {
            Loan loan = loanRepository.lockById(intent.getLoanId()).orElseThrow(LoanException::loanNotFound);
            LoanRepayment repayment = repaymentRepository.findByIdempotencyKey(key).orElseThrow();
            if (LoanRepayment.POSTED.equals(repayment.getStatus())) return new RepayOutcome(response(repayment, loan), true);
            if (!LoanRepayment.PENDING.equals(repayment.getStatus())) throw terminalFailure(repayment);
            if (transfer == null || !transfer.isPosted() || transfer.transactionId() == null) {
                if (transfer != null && "REJECTED".equals(transfer.status())) {
                    repayment.setStatus(transfer.isInsufficientFunds() ? LoanRepayment.INSUFFICIENT_FUNDS : LoanRepayment.REJECTED);
                    repaymentRepository.save(repayment);
                }
                return null; // Commit any terminal result before reporting an error to the caller.
            }

            BigDecimal payment = repayment.getAmount();
            List<LoanSchedule> rows = scheduleRepository.findByLoanIdOrderByInstallmentNo(loan.getLoanId());
            applyPayment(loan, rows, payment);
            updateAutoDebit(loan, repayment);
            scheduleRepository.saveAll(rows);
            loanRepository.save(loan);
            repayment.setTransactionId(transfer.transactionId());
            repayment.setStatus(LoanRepayment.POSTED);
            repaymentRepository.save(repayment);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("customerId", loan.getCustomerId());
            payload.put("accountId", loan.getAccountId());
            payload.put("loanReferenceNo", loan.getReferenceNo());
            payload.put("repaymentReferenceNo", repayment.getReferenceNo());
            payload.put("amount", payment);
            payload.put("outstandingPrincipal", money(loan.getOutstandingPrincipal()));
            payload.put("transactionId", transfer.transactionId());
            events.publish(LoanEvents.REPAYMENT_POSTED, loan.getReferenceNo(), payload);
            if (Loan.STATUS_CLOSED.equals(loan.getStatus())) {
                Map<String, Object> closed = new LinkedHashMap<>();
                closed.put("customerId", loan.getCustomerId());
                closed.put("accountId", loan.getAccountId());
                closed.put("loanReferenceNo", loan.getReferenceNo());
                events.publish(LoanEvents.CLOSED, loan.getReferenceNo(), closed);
            }

            log.info("[loan-service] Repayment {} of {} applied to loan {} → status={} outstanding={}",
                    repayment.getReferenceNo(), payment, loan.getReferenceNo(), loan.getStatus(), loan.getOutstandingPrincipal());
            return new RepayOutcome(response(repayment, loan), replayed);
        });
        if (result != null) return result;
        LoanRepayment current = repaymentRepository.findByIdempotencyKey(key).orElseThrow();
        if (LoanRepayment.POSTED.equals(current.getStatus())) {
            return new RepayOutcome(response(current, loanRepository.findById(current.getLoanId()).orElseThrow()), true);
        }
        if (!LoanRepayment.PENDING.equals(current.getStatus())) throw terminalFailure(current);
        throw pending();
    }

    @Scheduled(fixedDelayString = "${loan.repayment-recovery-ms:30000}", initialDelayString = "${loan.repayment-recovery-ms:30000}")
    public void recoverRepayments() {
        for (LoanRepayment repayment : repaymentRepository.findByStatusOrderByCreatedDateAsc(LoanRepayment.PENDING)) {
            try {
                settle(repayment.getIdempotencyKey(), "loan-repayment-recovery-" + repayment.getRepaymentId(), true);
            } catch (RuntimeException e) {
                log.warn("[loan-service] Repayment recovery not finished for {}: {}", repayment.getReferenceNo(), e.getMessage());
            }
        }
    }

    boolean isPosted(String key) {
        return repaymentRepository.findByIdempotencyKey(key)
                .map(r -> LoanRepayment.POSTED.equals(r.getStatus())).orElse(false);
    }

    private static LoanException pending() {
        return LoanException.coreUnavailable("Your repayment is still processing and will update automatically. Do not submit another payment.");
    }

    private static LoanException terminalFailure(LoanRepayment repayment) {
        if (LoanRepayment.INSUFFICIENT_FUNDS.equals(repayment.getStatus())) return LoanException.insufficientFunds();
        return new LoanException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "repayment-rejected",
                "Repayment Rejected", "The repayment was rejected. No loan payment was applied.");
    }

    private static void updateAutoDebit(Loan loan, LoanRepayment repayment) {
        String prefix = "AUTODEBIT-" + loan.getLoanId() + "-";
        if (!repayment.getIdempotencyKey().startsWith(prefix)) return;
        try {
            var date = java.time.LocalDate.parse(repayment.getIdempotencyKey().substring(prefix.length()));
            if (loan.getLastAutoDebitDate() == null || !loan.getLastAutoDebitDate().isAfter(date)) {
                loan.setLastAutoDebitDate(date);
                loan.setLastAutoDebitStatus(Loan.AUTODEBIT_PAID);
                loan.setLastAutoDebitAmount(repayment.getAmount());
            }
        } catch (java.time.format.DateTimeParseException ignored) {
            // A customer-supplied key with this prefix is still a normal manual repayment.
        }
    }

    /** Penalty first, then oldest unpaid rows, interest before principal. Updates loan status. */
    static void applyPayment(Loan loan, List<LoanSchedule> rows, BigDecimal payment) {
        BigDecimal remaining = payment;

        BigDecimal toPenalty = remaining.min(loan.getPenaltyDue());
        loan.setPenaltyDue(loan.getPenaltyDue().subtract(toPenalty));
        remaining = remaining.subtract(toPenalty);

        for (LoanSchedule row : rows) {
            if (remaining.signum() == 0) break;
            if (LoanSchedule.STATUS_PAID.equals(row.getStatus())) continue;

            BigDecimal interestPaid = row.getAmountPaid().min(row.getInterestDue());
            BigDecimal principalPaid = row.getAmountPaid().subtract(interestPaid);

            BigDecimal toInterest = remaining.min(row.getInterestDue().subtract(interestPaid));
            remaining = remaining.subtract(toInterest);
            BigDecimal toPrincipal = remaining.min(row.getPrincipalDue().subtract(principalPaid));
            remaining = remaining.subtract(toPrincipal);

            row.setAmountPaid(row.getAmountPaid().add(toInterest).add(toPrincipal));
            loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(toPrincipal));
            if (row.remaining().signum() == 0) row.setStatus(LoanSchedule.STATUS_PAID);
        }

        if (rows.stream().allMatch(r -> LoanSchedule.STATUS_PAID.equals(r.getStatus()))) {
            loan.setStatus(Loan.STATUS_CLOSED);
        } else if (rows.stream().noneMatch(r -> LoanSchedule.STATUS_OVERDUE.equals(r.getStatus()))) {
            loan.setStatus(Loan.STATUS_ACTIVE);
        }
    }

    private RepaymentResponse replay(LoanRepayment repayment, Long customerId, Long loanId, BigDecimal payment) {
        Loan loan = queries.ownedLoan(customerId, repayment.getLoanId());
        if (!repayment.getLoanId().equals(loanId) || repayment.getAmount().compareTo(payment) != 0) {
            throw LoanException.idempotencyConflict();
        }
        return response(repayment, loan);
    }

    private static RepaymentResponse response(LoanRepayment repayment, Loan loan) {
        return new RepaymentResponse(repayment.getReferenceNo(), loan.getLoanId(), money(repayment.getAmount()),
                repayment.getTransactionId(), loan.getStatus(), money(loan.getOutstandingPrincipal()), money(loan.getPenaltyDue()));
    }
}
