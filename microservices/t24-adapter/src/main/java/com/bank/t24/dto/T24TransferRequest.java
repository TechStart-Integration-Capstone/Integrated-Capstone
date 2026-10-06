package com.bank.t24.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class T24TransferRequest {

    @NotBlank(message = "Reference number is mandatory")
    private String referenceNo;

    @NotBlank(message = "Debit account number is mandatory")
    private String debitAccountNo;

    @NotBlank(message = "Credit account number is mandatory")
    private String creditAccountNo;

    @NotNull(message = "Amount is mandatory")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal amount;

    private String currency = "PHP";

    public T24TransferRequest() {}

    public T24TransferRequest(String referenceNo, String debitAccountNo, String creditAccountNo, BigDecimal amount, String currency) {
        this.referenceNo = referenceNo;
        this.debitAccountNo = debitAccountNo;
        this.creditAccountNo = creditAccountNo;
        this.amount = amount;
        this.currency = currency != null ? currency : "PHP";
    }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getDebitAccountNo() { return debitAccountNo; }
    public void setDebitAccountNo(String debitAccountNo) { this.debitAccountNo = debitAccountNo; }

    public String getCreditAccountNo() { return creditAccountNo; }
    public void setCreditAccountNo(String creditAccountNo) { this.creditAccountNo = creditAccountNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
