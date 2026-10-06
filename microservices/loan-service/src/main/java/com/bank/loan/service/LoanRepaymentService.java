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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bank.loan.service.AmortizationCalculator.money;

/**
 * POST /loans/{loanId}/repayments.
 *
 * One DB transaction: lock LOAN (UPDLOCK) → internal transfer customer → PH1000000LOAN (LOAN_REPAYMENT,
 * key LOAN-REPAY-{Idempotency-Key}) → on POSTED save LOAN_REPAYMENT and apply the money: penalty first,
 * then the oldest unpaid installments, interest before principal. Insufficient balance → 422, nothing saved.
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

        var previous = repaymentRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) return new RepayOutcome(replay(previous.get(), customerId, loanId, payment), true);

        return tx.execute(status -> {
            Loan loan = loanRepository.lockById(loanId)
                    .filter(l -> l.getCustomerId().equals(customerId))
                    .orElseThrow(LoanException::loanNotFound);

            // A concurrent request with the same key may have finished while we waited for the lock.
            var raced = repaymentRepository.findByIdempotencyKey(idempotencyKey);
            if (raced.isPresent()) return new RepayOutcome(replay(raced.get(), customerId, loanId, payment), true);

            if (Loan.STATUS_CLOSED.equals(loan.getStatus())) throw LoanException.validation("This loan is already fully paid.");

            List<LoanSchedule> rows = scheduleRepository.findByLoanIdOrderByInstallmentNo(loanId);
            BigDecimal owed = loan.getPenaltyDue().add(rows.stream()
                    .filter(r -> !LoanSchedule.STATUS_PAID.equals(r.getStatus()))
                    .map(LoanSchedule::remaining)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            if (payment.compareTo(owed) > 0) {
                throw LoanException.validation("amount is more than the total still owed (" + money(owed).toPlainString() + ").");
            }

            var account = reader.findAccountById(loan.getAccountId()).orElseThrow(LoanException::loanNotFound);
            OrchestratorClient.TransferResult transfer = orchestrator.transfer(
                    account.accountNumber(), props.getBankAccountNo(), payment, "LOAN_REPAYMENT",
                    "LOAN-REPAY-" + idempotencyKey, "Repayment for loan " + loan.getReferenceNo(), correlationId);
            if (transfer.isInsufficientFunds()) throw LoanException.insufficientFunds();
            if (!transfer.isPosted()) {
                log.warn("[loan-service] Repayment for loan {} not posted: {} {}", loanId, transfer.status(), transfer.reason());
                throw LoanException.coreUnavailable("The payment could not be completed right now. Please try again in a moment.");
            }

            LoanRepayment repayment = new LoanRepayment();
            repayment.setLoanId(loanId);
            repayment.setReferenceNo(LoanEvents.placeholderReference());
            repayment.setIdempotencyKey(idempotencyKey);
            repayment.setAmount(payment);
            repayment.setTransactionId(transfer.transactionId());
            repayment.setCreatedDate(events.nowUtc());
            repayment = repaymentRepository.save(repayment);
            repayment.setReferenceNo(events.reference("LRP", repayment.getRepaymentId()));
            repayment = repaymentRepository.save(repayment);

            applyPayment(loan, rows, payment);
            scheduleRepository.saveAll(rows);
            loanRepository.save(loan);

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
            return new RepayOutcome(response(repayment, loan), false);
        });
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
