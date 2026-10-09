package com.bank.account.savings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public final class SavingsRequests {
 private SavingsRequests() {}
 public record Goal(@NotNull @Positive Long accountId, @NotBlank @Size(max=80) String name,
  @NotBlank @Pattern(regexp="EMERGENCY|HOLIDAY|TRAVEL|LIFESTYLE|OTHER") String category,
  @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal target,
  @NotNull @FutureOrPresent LocalDate targetDate) {}
 public record Edit(@NotBlank @Size(max=80) String name,
  @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal target,
  @NotNull @FutureOrPresent LocalDate targetDate) {}
 public record Schedule(@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
  @NotBlank @Pattern(regexp="WEEKLY|MONTHLY|PAYDAY") String frequency,
  @NotNull @FutureOrPresent LocalDate nextDue, boolean enabled) {}
 public record Line(@NotNull UUID goalId,@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount) {}
 public record Operation(@NotNull @Positive Long accountId,@NotBlank @Pattern(regexp="ALLOCATE|RELEASE") String type,
  @NotEmpty @Size(max=50) List<@Valid Line> lines) {}
 public record Circle(@Valid @NotNull Goal goal,
  @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal myTarget,
  boolean shareProgress) {}
 public record Invite(@NotBlank @Size(max=100) String username,
  @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal target) {}
 public record Accept(@NotNull @Positive Long accountId,boolean shareProgress) {}
 public record Visibility(boolean shareProgress) {}
 public record Target(@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal target) {}
}