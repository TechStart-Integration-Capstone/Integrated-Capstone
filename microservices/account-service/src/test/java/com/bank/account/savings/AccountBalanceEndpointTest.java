package com.bank.account.savings;

import com.bank.account.controller.AccountBalanceController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountBalanceEndpointTest {
    private static final String URL = "/api/v1/accounts/savings/balance-summary";
    private static final String BREAKDOWN_URL = "/api/v1/accounts/savings/accounts/{id}/breakdown";
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Configuration
    @EnableWebMvc
    @Import({AccountBalanceController.class, SavingsController.class, SavingsService.class})
    static class Config {
        @Bean(destroyMethod = "destroy") SingleConnectionDataSource dataSource() {
            return new SingleConnectionDataSource(
                    "jdbc:h2:mem:balance-" + UUID.randomUUID() + ";MODE=MSSQLServer", true);
        }
        @Bean JdbcTemplate jdbc(SingleConnectionDataSource source) {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE SCHEMA app");
            jdbc.execute("CREATE SCHEMA t24");
            jdbc.execute("CREATE TABLE app.CUSTOMER(customer_id BIGINT PRIMARY KEY, status VARCHAR(20))");
            jdbc.execute("CREATE TABLE t24.ACCOUNT(account_id BIGINT PRIMARY KEY, customer_id BIGINT, currency VARCHAR(3), status VARCHAR(20) DEFAULT 'ACTIVE', account_type VARCHAR(30) DEFAULT 'SAVINGS')");
            jdbc.execute("CREATE TABLE app.SAVINGS_GOAL(goal_id VARCHAR(36) PRIMARY KEY, customer_id BIGINT, account_id BIGINT, circle_id VARCHAR(36), name VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO app.CUSTOMER VALUES(1,'ACTIVE'),(2,'ACTIVE'),(3,'FROZEN'),(4,'ACTIVE')");
            jdbc.update("INSERT INTO t24.ACCOUNT(account_id,customer_id,currency) VALUES(11,1,'PHP'),(12,1,'PHP'),(22,2,'PHP'),(13,1,'USD')");
            return jdbc;
        }
        @Bean SavingsCoreClient core() {
            var core = mock(SavingsCoreClient.class);
            when(core.breakdown(11L)).thenReturn(snapshot("20000", "9000"));
            when(core.breakdown(12L)).thenReturn(snapshot("5000", "4000"));
            return core;
        }
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean SavingsOperations operations() { return mock(SavingsOperations.class); }
    }

    private static SavingsCoreClient.Breakdown snapshot(String total, String available) {
        return new SavingsCoreClient.Breakdown(Map.of(
                "accountBalance", new BigDecimal(total),
                "availableBalance", new BigDecimal(available)), Map.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unset", "false", "true"})
    void summaryWorksWithSavingsDisabledUnsetOrEnabled(String flag) {
        var context = flag.equals("unset") ? runner : runner.withPropertyValues("app.savings.enabled=" + flag);
        context.run(app -> {
            assertThat(app).hasNotFailed();
            var mvc = MockMvcBuilders.webAppContextSetup(app).build();
            mvc.perform(get(URL).header("X-Auth-Customer-Id", 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBalance").value(25000))
                    .andExpect(jsonPath("$.availableBalance").value(13000))
                    .andExpect(jsonPath("$.accountCount").value(2));
            verify(app.getBean(SavingsCoreClient.class), never()).breakdown(22L);
            verify(app.getBean(SavingsCoreClient.class), never()).breakdown(13L);
            assertThat(app.getBeansOfType(SavingsController.class)).hasSize(flag.equals("true") ? 1 : 0);
            if (!flag.equals("true")) {
                mvc.perform(get("/api/v1/accounts/savings").header("X-Auth-Customer-Id", 1))
                        .andExpect(status().isNotFound());
                mvc.perform(post("/api/v1/accounts/savings/operations").header("X-Auth-Customer-Id", 1)
                        .contentType("application/json").content("{}")).andExpect(status().isNotFound());
                verifyNoInteractions(app.getBean(SavingsOperations.class));
            }
        });
    }

    @Test
    void missingInvalidOrInactiveCustomerCannotReadBalances() {
        runner.run(app -> {
            var mvc = MockMvcBuilders.webAppContextSetup(app).build();
            mvc.perform(get(URL)).andExpect(status().isBadRequest());
            mvc.perform(get(URL).header("X-Auth-Customer-Id", 0)).andExpect(status().isUnauthorized());
            mvc.perform(get(URL).header("X-Auth-Customer-Id", 3)).andExpect(status().isForbidden());
            mvc.perform(get(URL).header("X-Auth-Customer-Id", 99)).andExpect(status().isForbidden());
            verifyNoInteractions(app.getBean(SavingsCoreClient.class));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"unset", "false", "true"})
    void breakdownWorksWithSavingsDisabledUnsetOrEnabled(String flag) {
        var context = flag.equals("unset") ? runner : runner.withPropertyValues("app.savings.enabled=" + flag);
        context.run(app -> {
            assertThat(app).hasNotFailed();
            app.getBean(JdbcTemplate.class).update("""
                    INSERT INTO app.SAVINGS_GOAL(goal_id,customer_id,account_id,circle_id,name) VALUES
                    ('personal',1,11,NULL,'Emergency fund'),
                    ('circle',1,11,'shared','Trip'),
                    ('other-account',1,12,NULL,'Other account'),
                    ('other-customer',2,22,NULL,'Private goal')
                    """);
            when(app.getBean(SavingsCoreClient.class).breakdown(11L)).thenReturn(
                    new SavingsCoreClient.Breakdown(Map.of(
                            "accountBalance", new BigDecimal("20000"),
                            "availableBalance", new BigDecimal("9000"),
                            "reservedSavings", new BigDecimal("10000"),
                            "otherHolds", new BigDecimal("1000")),
                            Map.of("personal", new BigDecimal("6000"), "circle", new BigDecimal("3000"))));
            MockMvcBuilders.webAppContextSetup(app).build()
                    .perform(get(BREAKDOWN_URL, 11).header("X-Auth-Customer-Id", 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(11))
                    .andExpect(jsonPath("$.accountBalance").value(20000))
                    .andExpect(jsonPath("$.availableBalance").value(9000))
                    .andExpect(jsonPath("$.reservedSavings").value(10000))
                    .andExpect(jsonPath("$.otherHolds").value(1000))
                    .andExpect(jsonPath("$.personalReserved").value(6000))
                    .andExpect(jsonPath("$.circleReserved").value(3000))
                    .andExpect(jsonPath("$.unlistedReservations").value(1000))
                    .andExpect(jsonPath("$.allocations.length()").value(2))
                    .andExpect(jsonPath("$.allocations[?(@.kind == 'PERSONAL')].name").value("Emergency fund"))
                    .andExpect(jsonPath("$.allocations[?(@.kind == 'PINK_CIRCLE')].name").value("Trip"));
            verify(app.getBean(SavingsCoreClient.class)).breakdown(11L);
            verifyNoMoreInteractions(app.getBean(SavingsCoreClient.class));
        });
    }

    @Test
    void breakdownRejectsMissingInvalidInactiveOrUnownedAccountsBeforeCallingCore() {
        runner.run(app -> {
            var mvc = MockMvcBuilders.webAppContextSetup(app).build();
            mvc.perform(get(BREAKDOWN_URL, 11)).andExpect(status().isBadRequest());
            for (long customer : new long[]{0, 3, 99}) {
                mvc.perform(get(BREAKDOWN_URL, 11).header("X-Auth-Customer-Id", customer))
                        .andExpect(customer == 0 ? status().isUnauthorized() : status().isForbidden());
            }
            for (long account : new long[]{22, 13, 999}) {
                mvc.perform(get(BREAKDOWN_URL, account).header("X-Auth-Customer-Id", 1))
                        .andExpect(status().isForbidden());
            }
            app.getBean(JdbcTemplate.class).update("UPDATE t24.ACCOUNT SET status='FROZEN' WHERE account_id=11");
            mvc.perform(get(BREAKDOWN_URL, 11).header("X-Auth-Customer-Id", 1))
                    .andExpect(status().isForbidden());
            app.getBean(JdbcTemplate.class).update("UPDATE t24.ACCOUNT SET account_type='CURRENT' WHERE account_id=12");
            mvc.perform(get(BREAKDOWN_URL, 12).header("X-Auth-Customer-Id", 1))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(app.getBean(SavingsCoreClient.class));
        });
    }

    @Test
    void breakdownCoreFailureReturnsUnavailableInsteadOfInventingBalances() {
        runner.run(app -> {
            when(app.getBean(SavingsCoreClient.class).breakdown(11L))
                    .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
            MockMvcBuilders.webAppContextSetup(app).build()
                    .perform(get(BREAKDOWN_URL, 11).header("X-Auth-Customer-Id", 1))
                    .andExpect(status().isServiceUnavailable());
        });
    }

    @Test
    void partialCoreFailureDoesNotReturnAnIncompleteOrCachedBalance() {
        runner.run(app -> {
            when(app.getBean(SavingsCoreClient.class).breakdown(12L))
                    .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
            MockMvcBuilders.webAppContextSetup(app).build()
                    .perform(get(URL).header("X-Auth-Customer-Id", 1))
                    .andExpect(status().isServiceUnavailable());
        });
    }

    @Test
    void customerWithoutAccountsReceivesZeroWithoutCallingCore() {
        runner.run(app -> {
            MockMvcBuilders.webAppContextSetup(app).build()
                    .perform(get(URL).header("X-Auth-Customer-Id", 4))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBalance").value(0))
                    .andExpect(jsonPath("$.availableBalance").value(0))
                    .andExpect(jsonPath("$.accountCount").value(0));
            verifyNoInteractions(app.getBean(SavingsCoreClient.class));
        });
    }
}
