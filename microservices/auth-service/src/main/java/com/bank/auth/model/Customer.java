package com.bank.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER", schema = "app")
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
    @Column(name = "roles", nullable = false, length = 255) private String roles = "ROLE_CUSTOMER,ROLE_RETAIL_USER";
    @Column(name = "created_date", nullable = false, updatable = false) private LocalDateTime createdDate = LocalDateTime.now();

    public Customer() {}
    public Customer(String username, String passwordHash, String firstName, String lastName, String email, String contactNo) {
        this.username = username; this.passwordHash = passwordHash; this.firstName = firstName;
        this.lastName = lastName; this.email = email; this.contactNo = contactNo;
        this.status = "ACTIVE"; this.roles = "ROLE_CUSTOMER,ROLE_RETAIL_USER"; this.createdDate = LocalDateTime.now();
    }
    public Customer(String username, String passwordHash, String firstName, String lastName, String email, String contactNo, String roles) {
        this(username, passwordHash, firstName, lastName, email, contactNo);
        if (roles != null && !roles.isBlank()) {
            this.roles = roles;
        }
    }
    public Customer(Long customerId, String username, String passwordHash, String firstName, String lastName, String email, String contactNo) {
        this.customerId = customerId; this.username = username; this.passwordHash = passwordHash;
        this.firstName = firstName; this.lastName = lastName; this.email = email;
        this.contactNo = contactNo; this.status = "ACTIVE"; this.roles = "ROLE_CUSTOMER,ROLE_RETAIL_USER"; this.createdDate = LocalDateTime.now();
    }
    public Customer(Long customerId, String username, String passwordHash, String firstName, String lastName, String email, String contactNo, String roles) {
        this(customerId, username, passwordHash, firstName, lastName, email, contactNo);
        if (roles != null && !roles.isBlank()) {
            this.roles = roles;
        }
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
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    public java.util.List<String> getRolesList() {
        if (roles == null || roles.isBlank()) {
            return java.util.List.of("ROLE_CUSTOMER", "ROLE_RETAIL_USER");
        }
        return java.util.Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .toList();
    }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
