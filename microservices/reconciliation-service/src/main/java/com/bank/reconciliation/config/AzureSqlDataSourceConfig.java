package com.bank.reconciliation.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Primary datasource configuration for Azure SQL (SQL Server 2022).
 *
 * Previously named OracleDataSourceConfig — renamed in post-Phase-1 cleanup
 * after the primary OLTP database was migrated from Oracle XE to Azure SQL.
 *
 * Config prefix: spring.datasource.azure-sql
 * Bean qualifier: azureSqlDataSource / azureSqlEntityManagerFactory / azureSqlTransactionManager
 *
 * The "oracle" package path under model/repository is kept as-is to avoid
 * a larger refactor — these map to Azure SQL tables, not Oracle.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.bank.reconciliation.repository.oracle",
        entityManagerFactoryRef = "azureSqlEntityManagerFactory",
        transactionManagerRef   = "azureSqlTransactionManager"
)
public class AzureSqlDataSourceConfig {

    @Primary
    @Bean(name = "azureSqlDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.azure-sql")
    public DataSource azureSqlDataSource() {
        return DataSourceBuilder.create()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean(name = "entityManagerFactoryBuilder")
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        return new EntityManagerFactoryBuilder(vendorAdapter, new HashMap<>(), null);
    }

    @Primary
    @Bean(name = "azureSqlEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean azureSqlEntityManagerFactory(
            @Qualifier("entityManagerFactoryBuilder") EntityManagerFactoryBuilder builder,
            @Qualifier("azureSqlDataSource") DataSource dataSource) {

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect",      "org.hibernate.dialect.SQLServerDialect");
        props.put("hibernate.hbm2ddl.auto", "none");

        return builder
                .dataSource(dataSource)
                .packages("com.bank.reconciliation.model.oracle")
                .persistenceUnit("azureSql")
                .properties(props)
                .build();
    }

    @Primary
    @Bean(name = "azureSqlTransactionManager")
    public PlatformTransactionManager azureSqlTransactionManager(
            @Qualifier("azureSqlEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
