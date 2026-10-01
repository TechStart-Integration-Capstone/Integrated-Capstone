package com.bank.ledger.dto;

import java.util.List;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private Long expiresInMs;
    private Long customerId;
    private String username;
    private String fullName;
    private List<String> roles;

    public AuthResponse() {}

    public AuthResponse(String token, Long expiresInMs, Long customerId, String username, String fullName, List<String> roles) {
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresInMs = expiresInMs;
        this.customerId = customerId;
        this.username = username;
        this.fullName = fullName;
        this.roles = roles;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public Long getExpiresInMs() { return expiresInMs; }
    public void setExpiresInMs(Long expiresInMs) { this.expiresInMs = expiresInMs; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
