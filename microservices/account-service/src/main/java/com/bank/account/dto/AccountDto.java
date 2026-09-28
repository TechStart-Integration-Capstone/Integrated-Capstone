package com.bank.account.dto;

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
        this.accountId = accountId; this.customerId = customerId; this.accountNumber = accountNumber;
        this.accountType = accountType; this.currency = currency; this.currentBalance = currentBalance;
        this.formattedBalance = String.format("₱%,.4f", currentBalance != null ? currentBalance : BigDecimal.ZERO);
        this.status = status; this.createdDate = createdDate;
    }

    public Long getAccountId() { return accountId; }
    public Long getCustomerId() { return customerId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public String getFormattedBalance() { return formattedBalance; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
