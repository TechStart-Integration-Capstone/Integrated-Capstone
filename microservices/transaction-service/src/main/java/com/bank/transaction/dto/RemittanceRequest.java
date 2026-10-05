package com.bank.transaction.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class RemittanceRequest {

    @NotBlank(message = "Source account ID/Number is mandatory")
    private String sourceAccountId;

    @NotBlank(message = "Target account ID/Number is mandatory")
    private String targetAccountId;

    @NotNull(message = "Amount is mandatory")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal amount;

    private String currency = "PHP";

    public RemittanceRequest() {}

    public RemittanceRequest(String sourceAccountId, String targetAccountId, BigDecimal amount, String currency) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.currency = currency != null ? currency : "PHP";
    }

    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public String getTargetAccountId() { return targetAccountId; }
    public void setTargetAccountId(String targetAccountId) { this.targetAccountId = targetAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
