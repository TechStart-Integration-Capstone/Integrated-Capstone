package com.bank.t24.savings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record SavingsCommand(
 @NotNull UUID operationId, @NotNull @Positive Long customerId,
 @NotNull @Positive Long accountId, @NotBlank @Pattern(regexp="ALLOCATE|RELEASE") String type,
 @NotEmpty @Size(max=50) List<@Valid Line> lines) {
 public record Line(@NotNull UUID goalId,
  @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
  @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal target) {}
}