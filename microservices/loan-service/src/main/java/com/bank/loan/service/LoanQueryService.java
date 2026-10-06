package com.bank.loan.service;

import com.bank.loan.dto.LoanDtos.AutoDebit;
import com.bank.loan.dto.LoanDtos.LoanSummary;
import com.bank.loan.dto.LoanDtos.NextDue;
import com.bank.loan.dto.LoanDtos.ScheduleResponse;
import com.bank.loan.dto.LoanDtos.ScheduleRow;
import com.bank.loan.exception.LoanException;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanSchedule;
import com.bank.loan.repository.CustomerAccountReader;
import com.bank.loan.repository.LoanRepository;
import com.bank.loan.repository.LoanScheduleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.bank.loan.service.AmortizationCalculator.money;

/** GET /loans and GET /loans/{loanId}/schedule. A loan that isn't the caller's is reported as not found. */
@Service
public class LoanQueryService {

    private final LoanRepository loanRepository;
    private final LoanScheduleRepository scheduleRepository;
    private final CustomerAccountReader reader;

    public LoanQueryService(LoanRepository loanRepository, LoanScheduleRepository scheduleRepository,
                            CustomerAccountReader reader) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.reader = reader;
    }

    public List<LoanSummary> myLoans(Long customerId) {
        return loanRepository.findByCustomerIdOrderByLoanIdDesc(customerId).stream()
                .map(loan -> summary(loan, reader.findAccountById(loan.getAccountId())
                        .map(CustomerAccountReader.AccountRow::accountNumber).orElse(null)))
                .toList();
    }

    public ScheduleResponse schedule(Long customerId, Long loanId) {
        Loan loan = ownedLoan(customerId, loanId);
        List<ScheduleRow> rows = scheduleRepository.findByLoanIdOrderByInstallmentNo(loanId).stream()
                .map(s -> new ScheduleRow(s.getInstallmentNo(), s.getDueDate(), money(s.getPrincipalDue()),
                        money(s.getInterestDue()), money(s.totalDue()), money(s.getAmountPaid()), s.getStatus()))
                .toList();
        return new ScheduleResponse(loan.getLoanId(), loan.getReferenceNo(), rows);
    }

    public Loan ownedLoan(Long customerId, Long loanId) {
        return loanRepository.findById(loanId)
                .filter(l -> l.getCustomerId().equals(customerId))
                .orElseThrow(LoanException::loanNotFound);
    }

    public LoanSummary summary(Loan loan, String accountNo) {
        NextDue nextDue = scheduleRepository.findByLoanIdOrderByInstallmentNo(loan.getLoanId()).stream()
                .filter(s -> !LoanSchedule.STATUS_PAID.equals(s.getStatus()))
                .findFirst()
                .map(s -> new NextDue(s.getDueDate(), money(s.remaining())))
                .orElse(null);
        return new LoanSummary(loan.getLoanId(), loan.getReferenceNo(), accountNo, money(loan.getPrincipal()),
                LoanApplicationService.rate(loan.getAnnualRate()), loan.getTermMonths(), money(loan.getMonthlyInstallment()),
                money(loan.getOutstandingPrincipal()), money(loan.getPenaltyDue()), loan.getStatus(), nextDue,
                loan.getDisbursedDate(), loan.getMaturityDate(), loan.getFtReference(),
                loan.getLastAutoDebitStatus() == null ? null : new AutoDebit(loan.getLastAutoDebitDate(),
                        loan.getLastAutoDebitStatus(), loan.getLastAutoDebitAmount() == null ? null : money(loan.getLastAutoDebitAmount())));
    }
}
