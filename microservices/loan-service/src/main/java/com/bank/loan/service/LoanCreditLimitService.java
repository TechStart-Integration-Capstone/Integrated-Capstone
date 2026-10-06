package com.bank.loan.service;

import com.bank.loan.config.LoanProperties;
import com.bank.loan.dto.LoanDtos.Eligibility;
import com.bank.loan.exception.LoanException;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanApplication;
import com.bank.loan.repository.CustomerAccountReader;
import com.bank.loan.repository.LoanApplicationRepository;
import com.bank.loan.repository.LoanRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.bank.loan.service.AmortizationCalculator.money;

/**
 * Credit limit from the customer's credit history: the credit-score band's max amount is the total the
 * customer may owe at once. Principal still owed on open loans, and offers already being disbursed, use it up.
 * Checked when applying (caps or declines the offer) and again when accepting (so several offers taken out
 * side by side cannot exceed the limit together).
 */
@Service
public class LoanCreditLimitService {

    private static final Long NO_APPLICATION = -1L;

    private final LoanRepository loanRepository;
    private final LoanApplicationRepository applicationRepository;
    private final CustomerAccountReader reader;
    private final LoanDecisionEngine decisionEngine;
    private final LoanProperties props;

    public LoanCreditLimitService(LoanRepository loanRepository, LoanApplicationRepository applicationRepository,
                                  CustomerAccountReader reader, LoanDecisionEngine decisionEngine, LoanProperties props) {
        this.loanRepository = loanRepository;
        this.applicationRepository = applicationRepository;
        this.reader = reader;
        this.decisionEngine = decisionEngine;
        this.props = props;
    }

    /** Open-loan principal plus DISBURSING offers, leaving out the given application. */
    public BigDecimal existingDebt(Long customerId, Long excludeApplicationId) {
        BigDecimal open = nz(loanRepository.sumOpenPrincipal(customerId));
        BigDecimal disbursing = nz(applicationRepository.sumDisbursingAmount(customerId,
                excludeApplicationId != null ? excludeApplicationId : NO_APPLICATION));
        return money(open.add(disbursing));
    }

    public BigDecimal existingDebt(Long customerId) {
        return existingDebt(customerId, NO_APPLICATION);
    }

    /** Accepting this offer must still fit in the limit for the customer's current score. */
    public void checkAccept(LoanApplication app) {
        var customer = reader.findCustomer(app.getCustomerId()).orElseThrow(LoanException::accountNotOwned);
        var band = decisionEngine.bandFor(customer.creditScore());
        BigDecimal available = band == null ? BigDecimal.ZERO
                : LoanDecisionEngine.availableCredit(band.getValue(), existingDebt(app.getCustomerId(), app.getApplicationId()));
        if (app.getOfferedAmount().compareTo(available) > 0) {
            throw LoanException.creditLimitReached("Accepting this offer would exceed your credit limit. You can borrow up to "
                    + available.toPlainString() + " more right now. Please apply again for a smaller amount.");
        }
    }

    /** GET /loans/eligibility — what the customer can borrow right now. */
    public Eligibility eligibility(Long customerId) {
        var customer = reader.findCustomer(customerId).orElseThrow(LoanException::accountNotOwned);
        var band = decisionEngine.bandFor(customer.creditScore());
        BigDecimal debt = existingDebt(customerId);
        boolean overdue = loanRepository.existsByCustomerIdAndStatus(customerId, Loan.STATUS_OVERDUE);

        if (band == null || customer.creditScore() < props.getDeclineBelowScore()) {
            return new Eligibility(customer.creditScore(), band != null ? band.getKey() : null, money(BigDecimal.ZERO),
                    debt, money(BigDecimal.ZERO), money(props.getMinAmount()), null, null, false, "CREDIT_SCORE_TOO_LOW");
        }
        LoanProperties.Band rules = band.getValue();
        BigDecimal available = LoanDecisionEngine.availableCredit(rules, debt);
        String reason = overdue ? "EXISTING_LOAN_OVERDUE"
                : available.compareTo(props.getMinAmount()) < 0 ? "CREDIT_LIMIT_REACHED" : null;
        return new Eligibility(customer.creditScore(), band.getKey(), money(rules.getMaxAmount()), debt,
                overdue ? money(BigDecimal.ZERO) : available, money(props.getMinAmount()), rules.getMaxTerm(),
                LoanApplicationService.rate(rules.getAnnualRate()), reason == null, reason);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
