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
            jdbc.execute("CREATE TABLE t24.ACCOUNT(account_id BIGINT PRIMARY KEY, customer_id BIGINT, currency VARCHAR(3))");
            jdbc.update("INSERT INTO app.CUSTOMER VALUES(1,'ACTIVE'),(2,'ACTIVE'),(3,'FROZEN'),(4,'ACTIVE')");
            jdbc.update("INSERT INTO t24.ACCOUNT VALUES(11,1,'PHP'),(12,1,'PHP'),(22,2,'PHP'),(13,1,'USD')");
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
