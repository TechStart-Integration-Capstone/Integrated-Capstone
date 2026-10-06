package com.bank.transaction.orchestrator.interest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Configuration
@ConditionalOnProperty(name = "app.interest.enabled", havingValue = "true")
public class InterestConfiguration {
    // Wrap the secondary datasource so Boot still auto-configures the existing primary JPA datasource.
    public record AuditDatabase(HikariDataSource pool) implements AutoCloseable {
        @Override public void close() { pool.close(); }
    }

    @Bean(destroyMethod = "close")
    AuditDatabase interestAuditDatabase(@Value("${app.interest.postgres.url}") String url,
                                        @Value("${app.interest.postgres.username}") String username,
                                        @Value("${app.interest.postgres.password}") String password) {
        if (url.isBlank() || username.isBlank() || password.isBlank())
            throw new IllegalArgumentException("Interest PostgreSQL connection settings are required");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        config.setPoolName("InterestAuditPool");
        return new AuditDatabase(new HikariDataSource(config));
    }

    @Bean
    InterestAccrualStore interestAccrualStore(AuditDatabase audit) {
        return new InterestAccrualStore(new JdbcTemplate(audit.pool()),
                new TransactionTemplate(new DataSourceTransactionManager(audit.pool())));
    }

    @Bean
    InterestLedger interestLedger(DataSource dataSource, ObjectMapper mapper) {
        return new InterestLedger(new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), mapper);
    }

    @Bean
    InterestEodService interestEodService(InterestLedger ledger, InterestAccrualStore audit,
                                         @Value("${app.interest.start-date}") String start,
                                         @Value("${app.interest.zone}") String zone) {
        if (start.isBlank()) throw new IllegalArgumentException("INTEREST_START_DATE is required when interest EOD is enabled");
        return new InterestEodService(ledger, audit, LocalDate.parse(start), Clock.system(ZoneId.of(zone)));
    }
}
