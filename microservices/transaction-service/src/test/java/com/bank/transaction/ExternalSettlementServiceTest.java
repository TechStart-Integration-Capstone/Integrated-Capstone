package com.bank.transaction;

import com.bank.transaction.controller.ExternalSettlementController;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.service.ExternalSettlementService;
import com.bank.transaction.service.RemittanceOrchestratorService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.http.HttpStatus.*;

class ExternalSettlementServiceTest {
    private static final String REF="EXT-"+"a".repeat(43);
    private JdbcTemplate jdbc;
    private RemittanceOrchestratorService orchestrator;
    private ExternalSettlementService service;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:external-settlement;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.ACCOUNT(account_id BIGINT PRIMARY KEY,customer_id BIGINT,account_number VARCHAR(30),account_type VARCHAR(30),currency VARCHAR(3),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE dbo.LEDGER_TRANSACTION(transaction_id BIGINT PRIMARY KEY,from_account_id BIGINT,amount DECIMAL(18,4),transaction_type VARCHAR(30),status VARCHAR(20),transaction_date TIMESTAMP,reference_no VARCHAR(64))");
        jdbc.execute("CREATE TABLE dbo.OUTBOX_EVENT(transaction_id BIGINT,event_type VARCHAR(50))");
        jdbc.update("INSERT INTO dbo.ACCOUNT VALUES(1,42,'SOURCE','SAVINGS','PHP','ACTIVE'),(2,99,'PH1000000EXT','INTERNAL','PHP','ACTIVE')");
        jdbc.update("INSERT INTO dbo.LEDGER_TRANSACTION VALUES(10,1,20,'EXT_INSTAPAY_BDO','PENDING',?,?)",Timestamp.valueOf(LocalDateTime.now()),REF);
        orchestrator=mock(RemittanceOrchestratorService.class);
        service=new ExternalSettlementService(jdbc,orchestrator);
    }
    private String status() { return jdbc.queryForObject("SELECT status FROM dbo.LEDGER_TRANSACTION",String.class); }
    private RemittanceResponse response(String status) {
        var response=new RemittanceResponse();response.setStatus(status);return response;
    }
    @Test void persistedDetailsAndOwnerArePassedToRiskScreenedSaga() {
        when(orchestrator.processExternalRemittance(any(),eq(REF),eq(42L))).thenReturn(response("PROCESSING"));
        assertThat(service.settle(REF,42)).isEqualTo("PENDING");
        var request=ArgumentCaptor.forClass(RemittanceRequest.class);
        verify(orchestrator).processExternalRemittance(request.capture(),eq(REF),eq(42L));
        assertThat(request.getValue().getAmount()).isEqualByComparingTo("20");
        assertThat(request.getValue().getSourceAccountId()).isEqualTo("1");
        assertThat(request.getValue().getTargetAccountId()).isEqualTo("PH1000000EXT");
        assertThat(request.getValue().getTransactionType()).isEqualTo("EXT_INSTAPAY_BDO");
        assertThat(status()).isEqualTo("PENDING");
    }
    @Test void ownerMismatchNeverDispatches() {
        assertThatThrownBy(()->service.settle(REF,99)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(orchestrator);
    }
    @Test void untrustedInternalCallerNeverDispatches() {
        var controller=new ExternalSettlementController(service);
        for(String caller:new String[]{null,"loan-service","customer"}) {
            assertThatThrownBy(()->controller.settle(caller,new ExternalSettlementController.Request(REF,42L)))
                .isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(orchestrator);
    }
    @Test void missingClearingAccountFailsClosed() {
        jdbc.update("DELETE FROM dbo.ACCOUNT WHERE account_id=2");
        assertThatThrownBy(()->service.settle(REF,42)).isInstanceOf(ResponseStatusException.class);
        assertThat(status()).isEqualTo("PENDING");
        verifyNoInteractions(orchestrator);
    }
    @Test void cannotSettleLoansOrUnknownExternalTypes() {
        for(String type:new String[]{"LOAN_DISBURSEMENT","EXT_INSTAPAY_UNKNOWN"}) {
            jdbc.update("UPDATE dbo.LEDGER_TRANSACTION SET transaction_type=?",type);
            assertThatThrownBy(()->service.settle(REF,42)).isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(orchestrator);
    }
    @Test void pesonetDoesNotSkipDelayEvenThroughInternalEndpoint() {
        jdbc.update("UPDATE dbo.LEDGER_TRANSACTION SET transaction_type='EXT_PESONET_BDO'");
        assertThat(service.settle(REF,42)).isEqualTo("PENDING");
        verifyNoInteractions(orchestrator);
        jdbc.update("UPDATE dbo.LEDGER_TRANSACTION SET transaction_date=?",Timestamp.valueOf(LocalDateTime.now().minusSeconds(91)));
        when(orchestrator.processExternalRemittance(any(),eq(REF),eq(42L))).thenReturn(response("PROCESSING"));
        service.settle(REF,42);
        verify(orchestrator).processExternalRemittance(any(),eq(REF),eq(42L));
    }
    @Test void terminalRiskOrFundsRejectionMarksInstructionFailed() {
        when(orchestrator.processExternalRemittance(any(),eq(REF),eq(42L)))
                .thenThrow(new ResponseStatusException(UNPROCESSABLE_ENTITY,"Risk rejected"));
        assertThat(service.settle(REF,42)).isEqualTo("FAILED");
        assertThat(service.settle(REF,42)).isEqualTo("FAILED");
        verify(orchestrator).processExternalRemittance(any(),eq(REF),eq(42L));
    }
    @Test void unavailableAndInProgressResponsesRetainSameInstructionForRetry() {
        when(orchestrator.processExternalRemittance(any(),eq(REF),eq(42L)))
                .thenThrow(new ResponseStatusException(SERVICE_UNAVAILABLE,"Risk unavailable"))
                .thenThrow(new ResponseStatusException(CONFLICT,"Request in progress"))
                .thenReturn(response("PROCESSING"));
        assertThatThrownBy(()->service.settle(REF,42)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.settle(REF,42)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.settle(REF,42)).isEqualTo("PENDING");
        assertThat(status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.LEDGER_TRANSACTION",Integer.class)).isEqualTo(1);
    }
    @Test void legacyAlreadyDebitedInstructionDoesNotDebitAgain() {
        jdbc.update("INSERT INTO dbo.OUTBOX_EVENT VALUES(10,'TRANSACTION_SUCCESS')");
        assertThat(service.settle(REF,42)).isEqualTo("SUCCESS");
        assertThat(status()).isEqualTo("SUCCESS");
        service.settle(REF,42);
        verifyNoInteractions(orchestrator);
    }
}
