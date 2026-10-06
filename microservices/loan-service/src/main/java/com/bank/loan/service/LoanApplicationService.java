package com.bank.loan.service;

import com.bank.loan.config.LoanProperties;
import com.bank.loan.dto.LoanDtos.ApplicationResponse;
import com.bank.loan.dto.LoanDtos.ApplyRequest;
import com.bank.loan.dto.LoanDtos.Offer;
import com.bank.loan.exception.LoanException;
import com.bank.loan.model.Loan;
import com.bank.loan.model.LoanApplication;
import com.bank.loan.repository.CustomerAccountReader;
import com.bank.loan.repository.LoanApplicationRepository;
import com.bank.loan.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/** POST /loans/applications — validate, check ownership, decide (credit limit included), save + outbox in one transaction. */
@Service
public class LoanApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationService.class);

    private final LoanApplicationRepository applicationRepository;
    private final LoanRepository loanRepository;
    private final CustomerAccountReader reader;
    private final LoanDecisionEngine decisionEngine;
    private final LoanCreditLimitService creditLimit;
    private final LoanEvents events;
    private final LoanProperties props;
    private final TransactionTemplate tx;

    public LoanApplicationService(LoanApplicationRepository applicationRepository, LoanRepository loanRepository,
                                  CustomerAccountReader reader, LoanDecisionEngine decisionEngine,
                                  LoanCreditLimitService creditLimit, LoanEvents events,
                                  LoanProperties props, TransactionTemplate tx) {
        this.applicationRepository = applicationRepository;
        this.loanRepository = loanRepository;
        this.reader = reader;
        this.decisionEngine = decisionEngine;
        this.creditLimit = creditLimit;
        this.events = events;
        this.props = props;
        this.tx = tx;
    }

    public record ApplyOutcome(ApplicationResponse response, boolean replayed) {}

    public ApplyOutcome apply(Long customerId, String idempotencyKey, ApplyRequest request) {
        validateKey(idempotencyKey);

        // Repeated request: return the saved application.
        var existing = applicationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return new ApplyOutcome(replay(existing.get(), customerId), true);

        if (request.amount().compareTo(props.getMinAmount()) < 0) {
            throw LoanException.validation("amount must be at least " + props.getMinAmount().toPlainString());
        }
        if (request.termMonths() < props.getMinTerm() || request.termMonths() > props.getMaxTerm()) {
            throw LoanException.validation("termMonths must be between " + props.getMinTerm() + " and " + props.getMaxTerm());
        }

        var account = reader.findAccountByNumber(request.accountNo().trim())
                .filter(a -> a.customerId().equals(customerId))
                .orElseThrow(LoanException::accountNotOwned);
        if (!"ACTIVE".equals(account.status())) throw LoanException.validation("The account is not active.");

        var customer = reader.findCustomer(customerId)
                .orElseThrow(LoanException::accountNotOwned);
        boolean hasOverdue = loanRepository.existsByCustomerIdAndStatus(customerId, Loan.STATUS_OVERDUE);
        BigDecimal existingDebt = creditLimit.existingDebt(customerId);

        LoanDecisionEngine.Decision decision = decisionEngine.decide(customer.creditScore(), customer.monthlyIncome(),
                request.amount(), request.termMonths(), hasOverdue, existingDebt);

        try {
            LoanApplication saved = tx.execute(status -> save(customerId, idempotencyKey, request, account.accountId(),
                    customer.creditScore(), decision));
            log.info("[loan-service] Application {} customer={} score={} band={} decision={}", saved.getReferenceNo(),
                    customerId, customer.creditScore(), decision.band(), decision.decision());
            return new ApplyOutcome(toResponse(saved, decision.band()), false);
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key won the insert.
            LoanApplication winner = applicationRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
            return new ApplyOutcome(replay(winner, customerId), true);
        }
    }

    private LoanApplication save(Long customerId, String idempotencyKey, ApplyRequest request, Long accountId,
                                 int creditScore, LoanDecisionEngine.Decision decision) {
        LoanApplication app = new LoanApplication();
        app.setReferenceNo(LoanEvents.placeholderReference());
        app.setIdempotencyKey(idempotencyKey);
        app.setCustomerId(customerId);
        app.setAccountId(accountId);
        app.setRequestedAmount(request.amount());
        app.setRequestedTerm(request.termMonths());
        app.setCreditScore(creditScore);
        app.setDecision(decision.decision());
        app.setOfferedAmount(decision.amount());
        app.setOfferedTerm(decision.termMonths());
        app.setAnnualRate(decision.annualRate());
        app.setMonthlyInstallment(decision.monthlyInstallment());
        app.setDeclineReason(decision.declineReason());
        app.setStatus(LoanApplication.STATUS_DECIDED);
        app.setCreatedDate(events.nowUtc());
        app.setExpiresAt(app.getCreatedDate().plusDays(props.getOfferValidDays()));
        app = applicationRepository.save(app);
        app.setReferenceNo(events.reference("LAP", app.getApplicationId()));
        app = applicationRepository.save(app);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customerId);
        payload.put("accountId", accountId);
        payload.put("applicationReferenceNo", app.getReferenceNo());
        payload.put("decision", decision.decision());
        payload.put("creditScore", creditScore);
        payload.put("band", decision.band());
        payload.put("offeredAmount", decision.amount());
        payload.put("offeredTerm", decision.termMonths());
        payload.put("annualRate", decision.annualRate());
        payload.put("monthlyInstallment", decision.monthlyInstallment());
        payload.put("declineReason", decision.declineReason());
        events.publish(LoanEvents.APPLICATION_DECIDED, app.getReferenceNo(), payload);
        return app;
    }

    private ApplicationResponse replay(LoanApplication app, Long customerId) {
        if (!app.getCustomerId().equals(customerId)) throw LoanException.idempotencyConflict();
        var band = decisionEngine.bandFor(app.getCreditScore());
        return toResponse(app, band != null ? band.getKey() : null);
    }

    static ApplicationResponse toResponse(LoanApplication app, String band) {
        Offer offer = app.getOfferedAmount() == null ? null : new Offer(
                AmortizationCalculator.money(app.getOfferedAmount()), app.getOfferedTerm(),
                rate(app.getAnnualRate()), AmortizationCalculator.money(app.getMonthlyInstallment()));
        return new ApplicationResponse(app.getReferenceNo(), app.getDecision(), app.getCreditScore(), band, offer,
                app.getDeclineReason(), app.getStatus(), app.getExpiresAt().toInstant(ZoneOffset.UTC));
    }

    /** 18.000 → 18.0, 10.500 → 10.5 */
    static BigDecimal rate(BigDecimal annualRate) {
        BigDecimal r = annualRate.stripTrailingZeros();
        return r.scale() < 1 ? r.setScale(1) : r;
    }

    static void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw LoanException.validation("Idempotency-Key header is required (at most 64 characters).");
        }
    }
}
