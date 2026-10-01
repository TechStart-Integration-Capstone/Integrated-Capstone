package com.bank.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AccountDto {

    private Long accountId;
    private Long customerId;
    private String accountNumber;
    private String accountType;
    private String currency;
    private BigDecimal currentBalance;
    private String formattedBalance;
    private String status;
    private LocalDateTime createdDate;

    public AccountDto() {}

    public AccountDto(Long accountId, Long customerId, String accountNumber, String accountType,
                      String currency, BigDecimal currentBalance, String status, LocalDateTime createdDate) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.currency = currency;
        this.currentBalance = currentBalance;
        this.formattedBalance = String.format("₱%,.4f", currentBalance != null ? currentBalance : BigDecimal.ZERO);
        this.status = status;
        this.createdDate = createdDate;
    }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
        this.formattedBalance = String.format("₱%,.4f", currentBalance != null ? currentBalance : BigDecimal.ZERO);
    }

    public String getFormattedBalance() { return formattedBalance; }
    public void setFormattedBalance(String formattedBalance) { this.formattedBalance = formattedBalance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
}
