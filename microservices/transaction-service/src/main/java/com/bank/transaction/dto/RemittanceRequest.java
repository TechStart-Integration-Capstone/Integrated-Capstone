package com.bank.transaction.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class RemittanceRequest {

    public static final String TYPE_TRANSFER = "TRANSFER";
    public static final String TYPE_LOAN_DISBURSEMENT = "LOAN_DISBURSEMENT";
    public static final String TYPE_LOAN_REPAYMENT = "LOAN_REPAYMENT";

    @NotBlank(message = "Source account ID/Number is mandatory")
    private String sourceAccountId;

    @NotBlank(message = "Target account ID/Number is mandatory")
    private String targetAccountId;

    @NotNull(message = "Amount is mandatory")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal amount;

    private String currency = "PHP";

    // Never read from the public request body: only the internal transfer endpoint sets a loan type.
    @JsonIgnore
    private String transactionType = TYPE_TRANSFER;

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

    @JsonIgnore
    public String getTransactionType() { return transactionType; }
    @JsonIgnore
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
}
