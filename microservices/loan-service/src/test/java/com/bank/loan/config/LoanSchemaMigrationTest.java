package com.bank.loan.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LoanSchemaMigrationTest {

    @Test
    void splitsOnGoAndSkipsCommentOnlyBatches() {
        List<String> batches = LoanSchemaMigration.batches("-- header\nGO\nSELECT 1;\r\nGO\n  go  \nSELECT 2;\n");
        assertThat(batches).containsExactly("SELECT 1;", "SELECT 2;");
    }

    @Test
    void runsEveryGuardedBatchOfTheShippedScript() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new LoanSchemaMigration(jdbc).run(null);

        String script = new ClassPathResource(LoanSchemaMigration.SCRIPT).getContentAsString(StandardCharsets.UTF_8);
        List<String> batches = LoanSchemaMigration.batches(script);
        verify(jdbc, times(batches.size())).execute(anyString());
        assertThat(batches).hasSizeGreaterThanOrEqualTo(10)
                .allSatisfy(b -> assertThat(b).containsAnyOf("IF NOT EXISTS", "IF OBJECT_ID", "IF EXISTS"));
        assertThat(script).contains("PH1000000LOAN", "LOAN_REPAYMENT", "credit_score", "aggregate_id", "transaction_type");
    }

    @Test
    void manualCopyInScriptsFolderStaysIdentical() throws Exception {
        Path manual = Path.of("..", "..", "scripts", "migrate_phase6_loans.sql");
        if (!Files.exists(manual)) return; // not available when the module is built on its own
        String shipped = new ClassPathResource(LoanSchemaMigration.SCRIPT).getContentAsString(StandardCharsets.UTF_8);
        assertThat(Files.readString(manual).replace("\r\n", "\n")).isEqualTo(shipped.replace("\r\n", "\n"));
    }
}
