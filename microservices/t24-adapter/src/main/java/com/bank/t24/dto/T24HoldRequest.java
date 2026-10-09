package com.bank.t24.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class T24HoldRequest {

    private Long accountId;
    private String accountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String currency = "PHP";

    @NotBlank(message = "Reference number is required")
    private String referenceNo;

    public T24HoldRequest() {}

    public T24HoldRequest(Long accountId, String accountNumber, BigDecimal amount, String currency, String referenceNo) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.currency = (currency != null && !currency.isBlank()) ? currency : "PHP";
        this.referenceNo = referenceNo;
    }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
}
