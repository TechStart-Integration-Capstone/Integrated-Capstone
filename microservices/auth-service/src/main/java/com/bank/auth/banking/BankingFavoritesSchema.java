package com.bank.auth.banking;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.SQLException;

/** Additive, restart-safe migration for existing local Oracle installations. */
@Component
public class BankingFavoritesSchema implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public BankingFavoritesSchema(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void run(ApplicationArguments arguments) {
        try {
            jdbc.execute("CREATE TABLE BANKING_FAVORITE (customer_id NUMBER(19) NOT NULL, account_id NUMBER(19) NOT NULL, "
                    + "created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                    + "CONSTRAINT pk_banking_favorite PRIMARY KEY (customer_id, account_id), "
                    + "CONSTRAINT fk_favorite_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id), "
                    + "CONSTRAINT fk_favorite_account FOREIGN KEY (account_id) REFERENCES ACCOUNT(account_id))");
        } catch (Exception ex) {
            // Table or constraints already created in Oracle XE
        }
    }
}
