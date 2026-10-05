package com.bank.transaction.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Body of POST /internal/remittance/transfer (loan-service only, never routed by the gateway). */
public class InternalTransferRequest {

    @NotBlank(message = "Source account number is mandatory")
    private String sourceAccountNo;

    @NotBlank(message = "Target account number is mandatory")
    private String targetAccountNo;

    @NotNull(message = "Amount is mandatory")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal amount;

    @NotBlank(message = "Transaction type is mandatory")
    @Pattern(regexp = "LOAN_DISBURSEMENT|LOAN_REPAYMENT", message = "Transaction type must be LOAN_DISBURSEMENT or LOAN_REPAYMENT")
    private String transactionType;

    @NotBlank(message = "Idempotency key is mandatory")
    @Size(max = 80, message = "Idempotency key must be at most 80 characters")
    private String idempotencyKey;

    private String reason;

    public InternalTransferRequest() {}

    public String getSourceAccountNo() { return sourceAccountNo; }
    public void setSourceAccountNo(String sourceAccountNo) { this.sourceAccountNo = sourceAccountNo; }

    public String getTargetAccountNo() { return targetAccountNo; }
    public void setTargetAccountNo(String targetAccountNo) { this.targetAccountNo = targetAccountNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
