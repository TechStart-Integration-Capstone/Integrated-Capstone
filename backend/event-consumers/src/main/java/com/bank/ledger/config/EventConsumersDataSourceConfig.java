package com.bank.ledger.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
public class EventConsumersDataSourceConfig {

    // =========================================================================
    // 1. PRIMARY: PostgreSQL Immutable Audit & Reconciliation Log Data Source
    // =========================================================================
    @Primary
    @Bean
    @ConfigurationProperties("app.datasource.postgres")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource() {
        DataSourceProperties props = postgresDataSourceProperties();
        if (props.getUrl() == null || props.getUrl().isBlank()) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:ledgerdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL");
            ds.setUsername("sa");
            ds.setPassword("");
            ds.setDriverClassName("org.h2.Driver");
            ds.setPoolName("PostgresAuditPool");
            return ds;
        }
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean(name = "postgresEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("postgresDataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.show_sql", false);

        return builder
                .dataSource(dataSource)
                .packages("com.bank.ledger.model.postgres")
                .persistenceUnit("postgresUnit")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean(name = "postgresTransactionManager")
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.bank.ledger.repository.postgres",
            entityManagerFactoryRef = "postgresEntityManagerFactory",
            transactionManagerRef = "postgresTransactionManager"
    )
    static class PostgresRepositoriesConfig {}

    // =========================================================================
    // 2. SECONDARY: Oracle Read-Only Data Source (Reconciliation Verification)
    // =========================================================================
    @Bean
    @ConfigurationProperties("app.datasource.oracle")
    public DataSourceProperties oracleReadOnlyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "oracleReadOnlyDataSource")
    public DataSource oracleReadOnlyDataSource() {
        DataSourceProperties props = oracleReadOnlyDataSourceProperties();
        if (props.getUrl() == null || props.getUrl().isBlank()) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:ledgerdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=Oracle");
            ds.setUsername("SA");
            ds.setPassword("");
            ds.setDriverClassName("org.h2.Driver");
            ds.setPoolName("OracleReconReadOnlyPool");
            return ds;
        }
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "oracleEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("oracleReadOnlyDataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", false);

        return builder
                .dataSource(dataSource)
                .packages("com.bank.ledger.model.oracle")
                .persistenceUnit("oracleUnit")
                .properties(properties)
                .build();
    }

    @Bean(name = "oracleTransactionManager")
    public PlatformTransactionManager oracleTransactionManager(
            @Qualifier("oracleEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.bank.ledger.repository.oracle",
            entityManagerFactoryRef = "oracleEntityManagerFactory",
            transactionManagerRef = "oracleTransactionManager"
    )
    static class OracleRepositoriesConfig {}
}
