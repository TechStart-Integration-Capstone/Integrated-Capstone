package com.bank.auth.config;

import com.bank.auth.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Fix broken BCrypt hashes in DB — re-hash all customers with correct password
        String correctHash = passwordEncoder.encode("password123");
        long updated = customerRepository.findAll().stream()
                .filter(c -> !passwordEncoder.matches("password123", c.getPasswordHash()))
                .peek(c -> {
                    c.setPasswordHash(correctHash);
                    customerRepository.save(c);
                    log.info("[auth-service] Fixed password hash for user: {}", c.getUsername());
                })
                .count();
        if (updated > 0) {
            log.info("[auth-service] Fixed {} customer password hashes on startup.", updated);
        } else {
            log.info("[auth-service] All password hashes are valid, no fix needed.");
        }
    }
}
