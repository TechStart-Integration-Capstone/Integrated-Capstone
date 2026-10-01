package com.bank.ledger.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CustomerDto {

    private Long customerId;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String contactNo;
    private String status;
    private LocalDateTime createdDate;
    private List<AccountDto> accounts;

    public CustomerDto() {}

    public CustomerDto(Long customerId, String username, String firstName, String lastName,
                       String email, String contactNo, String status, LocalDateTime createdDate) {
        this.customerId = customerId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = firstName + " " + lastName;
        this.email = email;
        this.contactNo = contactNo;
        this.status = status;
        this.createdDate = createdDate;
    }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getContactNo() { return contactNo; }
    public void setContactNo(String contactNo) { this.contactNo = contactNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public List<AccountDto> getAccounts() { return accounts; }
    public void setAccounts(List<AccountDto> accounts) { this.accounts = accounts; }
}
