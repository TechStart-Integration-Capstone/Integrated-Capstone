package com.bank.ledger.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public class MutationRequest {

    @NotNull(message = "Account ID is mandatory")
    private Long accountId;

    @NotNull(message = "Mutation amount is mandatory")
    @Positive(message = "Mutation amount must be strictly positive")
    @Digits(integer = 14, fraction = 4, message = "Mutation amount must have at most 14 integer digits and up to 4 decimal places")
    private BigDecimal mutationAmount;

    @NotBlank(message = "Mutation operation is mandatory ('DEBIT' or 'CREDIT')")
    @Pattern(regexp = "(?i)DEBIT|CREDIT", message = "Operation must be 'DEBIT' or 'CREDIT'")
    private String operation;

    private String transactionType = "DEBIT"; // 'DEBIT', 'CREDIT', 'TRANSFER_INSTAPAY', 'TRANSFER_PESONET', 'TRANSFER_QRPH'

    private Long targetAccountId;

    private String currency = "PHP";

    private String idempotencyKey;

    public MutationRequest() {}

    public MutationRequest(Long accountId, BigDecimal mutationAmount, String operation, String transactionType, Long targetAccountId, String currency) {
        this.accountId = accountId;
        this.mutationAmount = mutationAmount;
        this.operation = operation;
        this.transactionType = transactionType;
        this.targetAccountId = targetAccountId;
        this.currency = currency != null ? currency : "PHP";
    }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getMutationAmount() { return mutationAmount; }
    public void setMutationAmount(BigDecimal mutationAmount) { this.mutationAmount = mutationAmount; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public Long getTargetAccountId() { return targetAccountId; }
    public void setTargetAccountId(Long targetAccountId) { this.targetAccountId = targetAccountId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    
}
