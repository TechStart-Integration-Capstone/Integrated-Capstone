package com.bank.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "username", nullable = false, unique = true, length = 50) private String username;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "first_name", nullable = false, length = 100) private String firstName;
    @Column(name = "last_name", nullable = false, length = 100) private String lastName;
    @Column(name = "email", nullable = false, unique = true, length = 150) private String email;
    @Column(name = "contact_no", nullable = false, length = 30) private String contactNo;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "created_date", nullable = false, updatable = false) private LocalDateTime createdDate = LocalDateTime.now();

    public Customer() {}
    public Customer(String username, String passwordHash, String firstName, String lastName, String email, String contactNo) {
        this.username = username; this.passwordHash = passwordHash; this.firstName = firstName;
        this.lastName = lastName; this.email = email; this.contactNo = contactNo;
        this.status = "ACTIVE"; this.createdDate = LocalDateTime.now();
    }

    public Long getCustomerId() { return customerId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getContactNo() { return contactNo; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
