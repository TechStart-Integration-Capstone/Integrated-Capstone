package com.bank.loan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies for /loans. Money is serialized with 2 decimal places. */
public final class LoanDtos {

    private LoanDtos() {}

    // ── Requests ────────────────────────────────────────────────────────────

    public record ApplyRequest(
            @NotBlank(message = "accountNo is required") String accountNo,
            @NotNull(message = "amount is required") @Positive(message = "amount must be positive")
            @Digits(integer = 14, fraction = 2, message = "amount allows at most 2 decimal places") BigDecimal amount,
            @NotNull(message = "termMonths is required") Integer termMonths) {}

    public record RepaymentRequest(
            @NotNull(message = "amount is required") @Positive(message = "amount must be positive")
            @Digits(integer = 14, fraction = 2, message = "amount allows at most 2 decimal places") BigDecimal amount) {}

    // ── Responses ───────────────────────────────────────────────────────────

    public record Offer(BigDecimal amount, Integer termMonths, BigDecimal annualRate, BigDecimal monthlyInstallment) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationResponse(String referenceNo, String decision, Integer creditScore, String band,
                                      Offer offer, String declineReason, String status, Instant expiresAt) {}

    public record NextDue(LocalDate dueDate, BigDecimal amount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoanSummary(Long loanId, String referenceNo, String accountNo, BigDecimal principal,
                              BigDecimal annualRate, Integer termMonths, BigDecimal monthlyInstallment,
                              BigDecimal outstandingPrincipal, BigDecimal penaltyDue, String status,
                              NextDue nextDue, LocalDate disbursedDate, LocalDate maturityDate, String ftReference) {}

    public record ScheduleRow(Integer installmentNo, LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue,
                              BigDecimal totalDue, BigDecimal amountPaid, String status) {}

    public record ScheduleResponse(Long loanId, String referenceNo, List<ScheduleRow> installments) {}

    public record RepaymentResponse(String referenceNo, Long loanId, BigDecimal amount, Long transactionId,
                                    String loanStatus, BigDecimal outstandingPrincipal, BigDecimal penaltyDue) {}

    public record EodResult(LocalDate businessDate, int loansAffected, int installmentsMarkedOverdue, int penaltiesCharged) {}
}
