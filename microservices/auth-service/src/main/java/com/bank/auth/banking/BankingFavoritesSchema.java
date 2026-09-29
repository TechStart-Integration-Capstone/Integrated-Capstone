package com.bank.auth.banking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive, restart-safe migration for existing local Oracle installations. */
@Component
public class BankingFavoritesSchema implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BankingFavoritesSchema.class);

    private final JdbcTemplate jdbc;

    public BankingFavoritesSchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            // Check if table already exists in Oracle's data dictionary before creating
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'BANKING_FAVORITE'",
                Integer.class
            );

            if (count != null && count > 0) {
                log.info("[auth-service] BANKING_FAVORITE table already exists, skipping creation.");
                return;
            }

            jdbc.execute(
                "CREATE TABLE BANKING_FAVORITE (" +
                "  customer_id  NUMBER(19) NOT NULL, " +
                "  account_id   NUMBER(19) NOT NULL, " +
                "  created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, " +
                "  CONSTRAINT pk_banking_favorite PRIMARY KEY (customer_id, account_id), " +
                "  CONSTRAINT fk_favorite_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id), " +
                "  CONSTRAINT fk_favorite_account  FOREIGN KEY (account_id)  REFERENCES ACCOUNT(account_id)" +
                ")"
            );
            log.info("[auth-service] BANKING_FAVORITE table created successfully.");
        } catch (org.springframework.dao.DataAccessException ex) {
            log.warn("[auth-service] BANKING_FAVORITE schema check note: {}", ex.getMessage());
        }
    }
}
