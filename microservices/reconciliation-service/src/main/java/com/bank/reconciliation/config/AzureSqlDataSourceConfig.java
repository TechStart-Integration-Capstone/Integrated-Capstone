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

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.bank.reconciliation.repository.sqlserver",
        entityManagerFactoryRef = "sqlServerEntityManagerFactory",
        transactionManagerRef = "sqlServerTransactionManager"
)
public class AzureSqlDataSourceConfig {

    @Primary
    @Bean(name = "sqlServerDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.azure-sql")
    public DataSource sqlServerDataSource() {
        return DataSourceBuilder.create()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }

    // Manually create EntityManagerFactoryBuilder since auto-config is excluded
    @Primary
    @Bean(name = "entityManagerFactoryBuilder")
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        return new EntityManagerFactoryBuilder(vendorAdapter, new HashMap<>(), null);
    }

    @Primary
    @Bean(name = "sqlServerEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean sqlServerEntityManagerFactory(
            @Qualifier("entityManagerFactoryBuilder") EntityManagerFactoryBuilder builder,
            @Qualifier("sqlServerDataSource") DataSource dataSource) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
        props.put("hibernate.hbm2ddl.auto", "none");
        return builder
                .dataSource(dataSource)
                .packages("com.bank.reconciliation.model.sqlserver")
                .persistenceUnit("sqlServer")
                .properties(props)
                .build();
    }

    @Primary
    @Bean(name = "sqlServerTransactionManager")
    public PlatformTransactionManager sqlServerTransactionManager(
            @Qualifier("sqlServerEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
