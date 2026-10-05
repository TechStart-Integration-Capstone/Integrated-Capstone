package com.bank.loan.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Applies db/phase6_loans.sql at startup: loan tables, the extra CUSTOMER/REMITTANCE/OUTBOX_EVENT columns,
 * the PH1000000LOAN bank account and the demo credit scores. Every statement is guarded, so it is a no-op
 * once applied.
 *
 * Needed because SQL Server only runs /mssql-server-setup-scripts.d on an empty data volume: anyone with an
 * existing volume would otherwise never get the Phase 6 schema. Fails startup on error, so a broken schema
 * shows up as an unhealthy loan-service instead of failing loan requests later.
 */
@Component
@ConditionalOnProperty(name = "loan.schema-migration.enabled", havingValue = "true", matchIfMissing = true)
public class LoanSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoanSchemaMigration.class);
    static final String SCRIPT = "db/phase6_loans.sql";

    private final JdbcTemplate jdbc;

    public LoanSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<String> batches = batches(new ClassPathResource(SCRIPT).getContentAsString(StandardCharsets.UTF_8));
        for (String batch : batches) {
            jdbc.execute(batch);
        }
        log.info("[loan-service] Phase 6 loan schema verified ({} guarded batches applied)", batches.size());
    }

    /** Splits a sqlcmd-style script on lines containing only GO; drops comment-only batches. */
    static List<String> batches(String script) {
        return Arrays.stream(script.replace("\r\n", "\n").split("(?im)^\\s*GO\\s*$"))
                .map(String::trim)
                .filter(b -> b.lines().anyMatch(l -> !l.isBlank() && !l.trim().startsWith("--")))
                .toList();
    }
}
