package com.bank.ledger.config;

import com.bank.ledger.model.oracle.Account;
import com.bank.ledger.model.oracle.Customer;
import com.bank.ledger.repository.oracle.AccountRepository;
import com.bank.ledger.repository.oracle.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (customerRepository.count() == 0) {
            log.info("Seeding Initial PayPink Oracle Retail Banking Data (Philippine FinTech Locale)...");

            // 1. Customer: Juan Dela Cruz (Retail Customer)
            Customer juan = new Customer(
                    "jdelacruz",
                    passwordEncoder.encode("CustomerPass123!"),
                    "Juan Dela Cruz",
                    "juan.delacruz@paypink.ph",
                    "+63 917 123 4567",
                    "ROLE_CUSTOMER,ROLE_RETAIL_USER"
            );
            juan = customerRepository.save(juan);

            // 2. Customer: Maria Santos (Teller / Operator)
            Customer maria = new Customer(
                    "teller_maria",
                    passwordEncoder.encode("TellerPass123!"),
                    "Maria Santos",
                    "maria.santos@paypink.ph",
                    "+63 918 765 4321",
                    "ROLE_TELLER,ROLE_OPERATOR"
            );
            maria = customerRepository.save(maria);

            // 3. Customer: Admin Joshua Reyes (System Administrator)
            Customer admin = new Customer(
                    "admin_reyes",
                    passwordEncoder.encode("AdminPass123!"),
                    "Joshua Reyes",
                    "joshua.reyes@paypink.ph",
                    "+63 919 555 0199",
                    "ROLE_ADMIN,ROLE_AUDITOR"
            );
            admin = customerRepository.save(admin);

            // 4. Accounts
            Account acc1 = new Account(
                    juan.getCustomerId(),
                    "ACC-PH-1001-8842",
                    "SAVINGS",
                    "PHP",
                    new BigDecimal("125450.0000")
            );
            accountRepository.save(acc1);

            Account acc2 = new Account(
                    maria.getCustomerId(),
                    "ACC-PH-1002-9931",
                    "CHECKING",
                    "PHP",
                    new BigDecimal("450200.5000")
            );
            accountRepository.save(acc2);

            Account acc3 = new Account(
                    admin.getCustomerId(),
                    "ACC-PH-1003-7714",
                    "TREASURY_SETTLEMENT",
                    "PHP",
                    new BigDecimal("10000000.0000")
            );
            accountRepository.save(acc3);

            log.info("Oracle Master Seed Complete: 3 Customers, 3 Localized Retail/Settlement Accounts (PHP ₱).");
        }
    }
}
