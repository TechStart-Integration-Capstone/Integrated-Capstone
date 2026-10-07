package com.bank.transaction;

import com.bank.transaction.controller.AdminTransactionMonitorController;
import com.bank.transaction.controller.TransactionQueryController;
import com.bank.transaction.dto.ActivityItem;
import com.bank.transaction.dto.AdminTransactionRow;
import com.bank.transaction.service.AdminTransactionMonitorService;
import com.bank.transaction.service.TransactionActivityService;
import com.bank.transaction.service.TransactionStatementReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionQueryControllerTest {

    private TransactionActivityService activityService;
    private TransactionStatementReportService reportService;
    private AdminTransactionMonitorService monitorService;

    private TransactionQueryController queryController;
    private AdminTransactionMonitorController adminController;

    @BeforeEach
    void setUp() {
        activityService = mock(TransactionActivityService.class);
        reportService = mock(TransactionStatementReportService.class);
        monitorService = mock(AdminTransactionMonitorService.class);

        queryController = new TransactionQueryController(activityService, reportService);
        adminController = new AdminTransactionMonitorController(monitorService);
    }

    @Test
    @DisplayName("getCustomerActivity returns customer activity when authenticated via X-Auth-Customer-Id")
    void getCustomerActivity_success() {
        ActivityItem item = new ActivityItem(
                101L, 1L, "001100000001", new BigDecimal("250.00"), "PHP",
                "P2P_REMITTANCE", "DEBIT", "PP-20261007-000000000101", "SUCCESS",
                LocalDateTime.now(), "Maria Santos", "001100000002"
        );
        when(activityService.getCustomerActivity(42L)).thenReturn(List.of(item));

        ResponseEntity<List<ActivityItem>> response = queryController.getCustomerActivity(42L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).counterpartyName()).isEqualTo("Maria Santos");
        verify(activityService, times(1)).getCustomerActivity(42L);
    }

    @Test
    @DisplayName("getCustomerActivity throws 401 when customer identity is missing")
    void getCustomerActivity_unauthorized() {
        assertThatThrownBy(() -> queryController.getCustomerActivity(null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing customer identity");
    }

    @Test
    @DisplayName("downloadReport returns PDF byte array with attachment headers")
    void downloadReport_success() {
        byte[] dummyPdf = "%PDF-1.4 sample content".getBytes();
        when(reportService.generatePdf(eq(42L), eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(dummyPdf);

        LocalDate now = LocalDate.now();
        ResponseEntity<byte[]> response = queryController.downloadReport(42L, null, 1L, now.minusDays(7), now);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("attachment; filename=");
        assertThat(response.getBody()).isEqualTo(dummyPdf);
    }

    @Test
    @DisplayName("getTodayTransactions returns monitor rows when caller has ROLE_ADMIN")
    void getTodayTransactions_adminAllowed() {
        AdminTransactionRow row = new AdminTransactionRow(
                "101", "TX-PH-101", OffsetDateTime.now(), "001100000001",
                "TRANSFER", "DEBIT", new BigDecimal("500.00"), "PHP", "Posted"
        );
        when(monitorService.today()).thenReturn(List.of(row));

        ResponseEntity<List<AdminTransactionRow>> response = adminController.getTodayTransactions("ROLE_ADMIN,ROLE_USER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).referenceNo()).isEqualTo("TX-PH-101");
    }

    @Test
    @DisplayName("getTodayTransactions throws 403 when caller lacks ROLE_ADMIN")
    void getTodayTransactions_forbiddenWithoutAdminRole() {
        assertThatThrownBy(() -> adminController.getTodayTransactions("ROLE_USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Administrator access required");
    }
}
