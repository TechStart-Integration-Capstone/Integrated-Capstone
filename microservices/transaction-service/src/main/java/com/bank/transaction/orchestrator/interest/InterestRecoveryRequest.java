package com.bank.transaction.orchestrator.interest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** The admin attests that BACKFILL contains the complete historical eligible-account manifest. */
public record InterestRecoveryRequest(
        @NotNull Mode mode,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 255) String sourceReference,
        @AssertTrue(message = "Confirm the complete historical backfill manifest") boolean confirmed,
        @NotNull @Size(max = 10000) List<@NotNull @Valid HistoricalAccount> accounts) {
    public enum Mode { BACKFILL }
    public record Approval(@NotBlank @Size(max = 1000) String reason,
                           @AssertTrue(message = "Confirm review of the source and complete manifest") boolean confirmed) {}
    public record HistoricalAccount(
            @Positive @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) long accountId,
            @NotBlank @Pattern(regexp = "SAVINGS|SAVINGS_ACCOUNT|LOAN") String accountType,
            @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 4)
            @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) BigDecimal eodBalance,
            @DecimalMin("0") @Digits(integer = 3, fraction = 4)
            @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) BigDecimal annualRate) {}
}
