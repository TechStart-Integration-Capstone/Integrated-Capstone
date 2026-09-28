package com.bank.auth.service;

import com.bank.auth.dto.AuthRequest;
import com.bank.auth.dto.AuthResponse;
import com.bank.auth.model.Customer;
import com.bank.auth.repository.CustomerRepository;
import com.bank.auth.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {
    private final CustomerRepository customerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(CustomerRepository customerRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse authenticate(AuthRequest request) {
        Customer customer = customerRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), customer.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }

        List<String> roles = List.of("ROLE_CUSTOMER", "ROLE_RETAIL_USER");
        String token = jwtTokenProvider.generateToken(customer.getCustomerId(), customer.getUsername(), roles);
        return new AuthResponse(token, 86400000L, customer.getCustomerId(), customer.getUsername(),
                customer.getFirstName() + " " + customer.getLastName(), roles);
    }

    public AuthResponse getDemoToken() {
        return authenticate(new AuthRequest("lviernes", "password123"));
    }
}
