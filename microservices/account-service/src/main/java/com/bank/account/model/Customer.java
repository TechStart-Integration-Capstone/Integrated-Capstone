package com.bank.account.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "customer_id") private Long customerId;
    @Column(name = "username") private String username;
    @Column(name = "first_name") private String firstName;
    @Column(name = "last_name") private String lastName;
    @Column(name = "email") private String email;
    @Column(name = "contact_no") private String contactNo;
    @Column(name = "status") private String status;
    @Column(name = "created_date") private LocalDateTime createdDate;
    @Column(name = "password_hash") private String passwordHash;

    public Long getCustomerId() { return customerId; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getContactNo() { return contactNo; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
