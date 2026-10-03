package com.bank.auth.banking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Creates the Azure SQL favorites table when it has not yet been provisioned. */
@Component
public class BankingFavoritesSchema implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BankingFavoritesSchema.class);

    private final JdbcTemplate jdbc;

    public BankingFavoritesSchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        jdbc.execute("""
                IF OBJECT_ID(N'dbo.BANKING_FAVORITE', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.BANKING_FAVORITE (
                        customer_id BIGINT NOT NULL,
                        account_id BIGINT NOT NULL,
                        created_date DATETIME2(7) NOT NULL DEFAULT SYSDATETIME(),
                        CONSTRAINT pk_banking_favorite PRIMARY KEY (customer_id, account_id),
                        CONSTRAINT fk_favorite_customer FOREIGN KEY (customer_id) REFERENCES dbo.CUSTOMER(customer_id),
                        CONSTRAINT fk_favorite_account FOREIGN KEY (account_id) REFERENCES dbo.ACCOUNT(account_id)
                    );
                END
                """);
        log.info("[auth-service] BANKING_FAVORITE table is ready.");
    }
}
