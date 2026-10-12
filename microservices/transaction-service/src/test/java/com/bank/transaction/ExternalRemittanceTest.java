package com.bank.transaction;

import com.bank.transaction.client.*;
import com.bank.transaction.dto.*;
import com.bank.transaction.model.*;
import com.bank.transaction.repository.*;
import com.bank.transaction.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExternalRemittanceTest {
    private static final String REF="EXT-"+"b".repeat(43);
    private RemittanceRepository remittances;
    private TransactionRepository transactions;
    private OutboxEventRepository outbox;
    private JdbcTemplate jdbc;
    private RiskEngineClient risk;
    private T24HoldClient holds;
    private T24AdapterClient core;
    private RemittanceLedgerService ledger;
    private RemittanceOrchestratorService orchestrator;
    private final ObjectMapper json=new ObjectMapper();

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        remittances=mock(RemittanceRepository.class);transactions=mock(TransactionRepository.class);
        outbox=mock(OutboxEventRepository.class);jdbc=mock(JdbcTemplate.class);
        risk=mock(RiskEngineClient.class);holds=mock(T24HoldClient.class);core=mock(T24AdapterClient.class);
        var redis=mock(StringRedisTemplate.class);
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(),anyString(),any())).thenReturn(true);
        ledger=new RemittanceLedgerService(remittances,transactions,outbox,jdbc,holds);
        orchestrator=new RemittanceOrchestratorService(remittances,ledger,risk,core,redis,json,mock(RiskDecisionPublisher.class));
        account(1,42,"SOURCE","100","20");
        account(2,99,"PH1000000EXT","0","0");
        when(remittances.save(any())).thenAnswer(i->i.getArgument(0));
        when(risk.evaluateRisk(any(),any(),any(),any(),any())).thenReturn(new RiskResult(new BigDecimal("0.10"),"ALLOW",List.of()));
        when(holds.placeHold(any(),any(),any(),any(),any())).thenReturn(new T24HoldClient.HoldResult(true,1L,REF,"ACTIVE",null));
        when(core.executeTransfer(any(),any(),any(),any(),any(),any())).thenReturn(new T24Result("POSTED","FT-EXT",null,null,false));
        when(jdbc.update(startsWith("UPDATE dbo.LEDGER_TRANSACTION SET status='SUCCESS'"),any(),any(),any(),any())).thenReturn(1);
        when(jdbc.queryForList(contains("WHERE reference_no = ? ORDER BY"),eq(Long.class),eq(REF))).thenReturn(List.of(10L));
    }
    private void account(long id,long owner,String number,String balance,String held) {
        var rows=List.<Map<String,Object>>of(Map.of("account_id",id,"customer_id",owner,"account_number",number,
                "current_balance",new BigDecimal(balance),"held_balance",new BigDecimal(held)));
        when(jdbc.queryForList(anyString(),eq(number),eq(number))).thenReturn(rows);
        when(jdbc.queryForList(anyString(),eq(""+id),eq(""+id))).thenReturn(rows);
    }
    private RemittanceRequest request() {
        var request=new RemittanceRequest("1","PH1000000EXT",new BigDecimal("20"),"PHP");
        request.setTransactionType("EXT_INSTAPAY_BDO");return request;
    }
    @Test void approvedExternalTransferScreensThenHoldsThenPostsAndCompletesOriginalRow() throws Exception {
        var result=orchestrator.processExternalRemittance(request(),REF,42L);
        assertThat(result.getStatus()).isEqualTo("POSTED");
        assertThat(result.getTransactionId()).isEqualTo(10L);
        var order=inOrder(risk,holds,core);
        order.verify(risk).evaluateRisk(eq(1L),eq(2L),eq(new BigDecimal("20")),eq("PHP"),any());
        order.verify(holds).placeHold(eq(1L),eq("SOURCE"),eq(new BigDecimal("20")),eq("PHP"),eq(REF));
        order.verify(core).executeTransfer(eq(REF),eq("SOURCE"),eq("PH1000000EXT"),eq(new BigDecimal("20")),eq("PHP"),any());
        verify(transactions,never()).save(any());
        verify(jdbc,never()).update(contains("UPDATE dbo.ACCOUNT"),any(Object[].class));
        verify(outbox).save(argThat(e->e.getTransactionId().equals(10L) && "REMITTANCE_COMPLETED".equals(e.getEventType())));
        verify(remittances,atLeastOnce()).save(argThat(r->new BigDecimal("0.10").equals(r.getRiskScore())));
    }
    @Test void riskRejectionNeverTouchesHoldOrCore() {
        when(risk.evaluateRisk(any(),any(),any(),any(),any())).thenReturn(new RiskResult(new BigDecimal("0.95"),"REJECT",List.of()));
        assertThatThrownBy(()->orchestrator.processExternalRemittance(request(),REF,42L)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(holds,core);
        verify(outbox,never()).save(any());
    }
    @Test void missingRiskScoreFailsClosed() {
        when(risk.evaluateRisk(any(),any(),any(),any(),any())).thenReturn(new RiskResult(null,"ALLOW",List.of()));
        assertThatThrownBy(()->orchestrator.processExternalRemittance(request(),REF,42L)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(holds,core);
    }
    @Test void unavailableRiskFailsClosed() {
        when(risk.evaluateRisk(any(),any(),any(),any(),any())).thenReturn(new RiskResult(BigDecimal.ZERO,"UNAVAILABLE",List.of()));
        assertThatThrownBy(()->orchestrator.processExternalRemittance(request(),REF,42L)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(holds,core);
    }
    @Test void reservationsPlacedAfterSubmissionAreNotSpendable() {
        account(1,42,"SOURCE","50","40");
        assertThatThrownBy(()->orchestrator.processExternalRemittance(request(),REF,42L)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(holds,core);
    }
    @Test void pendingCoreKeepsInstructionPendingWithoutSuccessOutbox() {
        when(core.executeTransfer(any(),any(),any(),any(),any(),any())).thenReturn(new T24Result("PROCESSING",null,null,null,false));
        var result=orchestrator.processExternalRemittance(request(),REF,42L);
        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        verify(jdbc,never()).update(startsWith("UPDATE dbo.LEDGER_TRANSACTION SET status='SUCCESS'"),any(Object[].class));
        verifyNoInteractions(outbox);
    }
    @Test void replayedPostedTransferDoesNotCallRiskOrCoreAgain() {
        var posted=new Remittance(REF,1L,2L,new BigDecimal("20"),"PHP","POSTED");
        posted.setTransactionType("EXT_INSTAPAY_BDO");
        when(remittances.findByCallerCustomerIdAndIdempotencyKey(42L,REF)).thenReturn(Optional.of(posted));
        assertThat(orchestrator.processExternalRemittance(request(),REF,42L).getTransactionId()).isEqualTo(10L);
        verifyNoInteractions(risk,holds,core,outbox);
    }
    @Test void localCommitFailureRecoversTheSameInstructionWithoutPlacingAnotherHold() {
        when(jdbc.update(startsWith("UPDATE dbo.LEDGER_TRANSACTION SET status='SUCCESS'"),any(),any(),any(),any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Database unavailable"))
                .thenReturn(1);
        assertThat(orchestrator.processExternalRemittance(request(),REF,42L).getStatus()).isEqualTo("PROCESSING");
        var saved=org.mockito.ArgumentCaptor.forClass(Remittance.class);
        verify(remittances,atLeastOnce()).save(saved.capture());
        Remittance recovery=saved.getValue();
        assertThat(recovery.getFtReference()).isEqualTo("FT-EXT");
        assertThat(recovery.getRiskScore()).isEqualByComparingTo("0.10");
        verifyNoInteractions(outbox);
        assertThat(orchestrator.executeCoreBankingSaga(recovery,"recovery").getTransactionId()).isEqualTo(10L);
        verify(holds,times(1)).placeHold(any(),any(),any(),any(),eq(REF));
        verify(holds,never()).releaseHold(any());
        verify(core,times(2)).executeTransfer(eq(REF),any(),any(),any(),any(),any());
        verify(outbox,times(1)).save(argThat(e->e.getTransactionId().equals(10L)));
        verify(transactions,never()).save(any());
    }
    @Test void coreFailureReleasesHoldAndMarksExternalInstructionFailed() {
        when(core.executeTransfer(any(),any(),any(),any(),any(),any())).thenReturn(new T24Result("REJECTED",null,null,"Frozen",false));
        assertThatThrownBy(()->orchestrator.processExternalRemittance(request(),REF,42L)).isInstanceOf(ResponseStatusException.class);
        verify(holds).releaseHold(REF);
        verify(jdbc).update(startsWith("UPDATE dbo.LEDGER_TRANSACTION SET status='FAILED'"),eq(REF));
    }
}
