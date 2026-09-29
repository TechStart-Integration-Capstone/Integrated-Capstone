package com.bank.auth.banking;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionReportServiceTest {
    final BankingService.Account account = new BankingService.Account(1, "PP-1234567890", "EVERYDAY_ACCOUNT", "PHP", BigDecimal.TEN, "ACTIVE");
    final BankingService.Profile profile = new BankingService.Profile("Ana", "Ana Peña", "ana", "ana@example.com", List.of(account));

    @Test
    void pdfContainsEntireHistoryAndExcludesPendingFromTotals() {
        var rows = new ArrayList<TransactionReportService.Row>();
        for (int i = 0; i < 205; i++) {
            rows.add(new TransactionReportService.Row(LocalDateTime.of(2026, 1, 2, 0, 0), "REFERENCE-" + i, "TRANSFER_OUT", "SUCCESS", "DEBIT", BigDecimal.ONE, "Alex"));
        }
        rows.add(new TransactionReportService.Row(LocalDateTime.of(2026, 1, 2, 0, 0), "PENDING-REF", "EXT_PESONET_BDO", "PENDING", "DEBIT", new BigDecimal("999"), "Alex"));
        byte[] pdf = TransactionReportService.pdf(profile, account, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), rows);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        String pdfStr = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(pdfStr).startsWith("%PDF-1.4");
        assertThat(pdfStr).contains("REFERENCE-204", "PENDING-REF", "Completed money out: PHP 205.00", "Ana Pe", "Account ending 7890");
    }

    @Test
    void emptyReportStillDownloads() {
        byte[] pdf = TransactionReportService.pdf(profile, account, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), List.of());
        assertThat(pdf).isNotNull();
        String pdfStr = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(pdfStr).contains("No transactions occurred", "Completed money in: PHP 0.00");
    }

    @Test
    void preventsOtherCustomersAccountsAndInvalidDatesBeforeQuerying() {
        var banking = mock(BankingService.class);
        var jdbc = mock(JdbcTemplate.class);
        when(banking.profile("token")).thenReturn(profile);
        var reports = new TransactionReportService(banking, jdbc);
        assertThatThrownBy(() -> reports.generate("token", 2, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> reports.generate("token", 1, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> reports.generate("token", 1, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc);
    }
}
