package com.bank.auth.dto;

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
        this.token = token; this.tokenType = "Bearer"; this.expiresInMs = expiresInMs;
        this.customerId = customerId; this.username = username; this.fullName = fullName; this.roles = roles;
    }

    public String getToken() { return token; }
    public String getTokenType() { return tokenType; }
    public Long getExpiresInMs() { return expiresInMs; }
    public Long getCustomerId() { return customerId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public List<String> getRoles() { return roles; }
}
