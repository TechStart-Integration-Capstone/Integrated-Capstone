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
@EnableJpaRepositories(
        basePackages = "com.bank.ledger.repository.sqlserver",
        entityManagerFactoryRef = "azureSqlEntityManagerFactory",
        transactionManagerRef = "azureSqlTransactionManager"
)
public class AzureSqlDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("app.datasource.azure-sql")
    public DataSourceProperties azureSqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "azureSqlDataSource")
    @ConfigurationProperties("app.datasource.azure-sql.hikari")
    public HikariDataSource azureSqlDataSource() {
        DataSourceProperties props = azureSqlDataSourceProperties();
        if (props.getUrl() == null || props.getUrl().isBlank()) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:ledgerdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MSSQLServer");
            ds.setUsername("SA");
            ds.setPassword("");
            ds.setDriverClassName("org.h2.Driver");
            ds.setPoolName("AzureSqlMasterPool");
            return ds;
        }
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean(name = "azureSqlEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean azureSqlEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("azureSqlDataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", false);

        return builder
                .dataSource(dataSource)
                .packages("com.bank.ledger.model.sqlserver")
                .persistenceUnit("azureSqlUnit")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean(name = "azureSqlTransactionManager")
    public PlatformTransactionManager azureSqlTransactionManager(
            @Qualifier("azureSqlEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
