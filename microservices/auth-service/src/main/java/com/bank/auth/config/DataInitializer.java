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
        // Repair only the original demo users. Never overwrite registered customers' passwords.
        // Also handles $2b$ / $2y$ hash prefixes that Spring's BCryptPasswordEncoder does not
        // recognise — those will cause matches() to throw or silently return false.
        String correctHash = passwordEncoder.encode("password123");
        long updated = customerRepository.findAll().stream()
                .filter(c -> java.util.Set.of("lviernes", "arosales", "glim").contains(c.getUsername()))
                .filter(c -> {
                    String hash = c.getPasswordHash();
                    if (hash == null) return true;
                    // Spring BCryptPasswordEncoder only handles $2a$ prefix.
                    // $2b$ / $2y$ are bcrypt variants that it rejects; treat them as broken.
                    if (!hash.startsWith("$2a$")) return true;
                    try {
                        return !passwordEncoder.matches("password123", hash);
                    } catch (Exception e) {
                        return true; // unrecognised format — repair it
                    }
                })
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
