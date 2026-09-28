package com.bank.analytics;
import com.bank.analytics.dto.AnalyticsEvent;
import com.bank.analytics.service.AnalyticsAggregator;
import com.bank.analytics.service.AnalyticsKafkaConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
/** Pure unit tests — no Spring context, no Kafka broker, no DB. */
class AnalyticsAggregatorTest {
    private AnalyticsAggregator aggregator;
    private AnalyticsKafkaConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @BeforeEach void setUp() {
        aggregator = new AnalyticsAggregator();
        consumer   = new AnalyticsKafkaConsumer(aggregator, objectMapper);
    }
    private AnalyticsEvent evt(long txId,long accId,long custId,String op,String amount,String after){
        AnalyticsEvent e=new AnalyticsEvent();
        e.setTransactionId(txId);e.setAccountId(accId);e.setCustomerId(custId);
        e.setOperation(op);e.setAmount(new BigDecimal(amount));
        e.setAfterBalance(new BigDecimal(after));e.setCurrency("PHP");
        e.setReferenceNo("REF-"+txId);e.setTimestamp("2026-09-28T10:00:00");
        return e;
    }
    // ── ingest + getSummary ───────────────────────────────────────────────────
    @Test @DisplayName("getSummary: fresh aggregator reports zero totals")
    void getSummary_freshAggregator_zeroTotals(){
        Map<String,Object> s=aggregator.getSummary();
        assertThat((Long)s.get("totalEventsProcessed")).isZero();
        assertThat((Long)s.get("totalDebits")).isZero();
        assertThat((Long)s.get("totalCredits")).isZero();
        assertThat(s.get("status")).isEqualTo("UP");
        assertThat(s.get("service")).isEqualTo("analytics-service");
    }
    @Test @DisplayName("ingest DEBIT: totalDebits increments, CREDIT count stays zero")
    void ingest_debit_incrementsDebitCount(){
        aggregator.ingest(evt(1L,101L,10L,"DEBIT","200.00","800.00"));
        Map<String,Object> s=aggregator.getSummary();
        assertThat((Long)s.get("totalEventsProcessed")).isEqualTo(1L);
        assertThat((Long)s.get("totalDebits")).isEqualTo(1L);
        assertThat((Long)s.get("totalCredits")).isZero();
    }
    @Test @DisplayName("ingest CREDIT: totalCredits increments, DEBIT count stays zero")
    void ingest_credit_incrementsCreditCount(){
        aggregator.ingest(evt(2L,102L,11L,"CREDIT","500.00","1500.00"));
        Map<String,Object> s=aggregator.getSummary();
        assertThat((Long)s.get("totalCredits")).isEqualTo(1L);
        assertThat((Long)s.get("totalDebits")).isZero();
    }
    @Test @DisplayName("ingest: totalDebitAmountPHP accumulates correctly across multiple events")
    void ingest_multipleDebits_amountAccumulates(){
        aggregator.ingest(evt(1L,101L,10L,"DEBIT","100.00","900.00"));
        aggregator.ingest(evt(2L,101L,10L,"DEBIT","200.00","700.00"));
        aggregator.ingest(evt(3L,101L,10L,"DEBIT","300.00","400.00"));
        Map<String,Object> s=aggregator.getSummary();
        assertThat((Long)s.get("totalDebits")).isEqualTo(3L);
        assertThat((BigDecimal)s.get("totalDebitAmountPHP")).isEqualByComparingTo("600.00");
    }
    @Test @DisplayName("ingest: uniqueAccountsActive counts distinct account IDs")
    void ingest_uniqueAccounts_countedCorrectly(){
        aggregator.ingest(evt(1L,101L,10L,"DEBIT","100.00","900.00"));
        aggregator.ingest(evt(2L,101L,10L,"CREDIT","50.00","950.00")); // same account
        aggregator.ingest(evt(3L,102L,11L,"DEBIT","200.00","800.00")); // different account
        Map<String,Object> s=aggregator.getSummary();
        assertThat((Integer)s.get("uniqueAccountsActive")).isEqualTo(2);
    }
    @Test @DisplayName("ingest: uniqueCustomersActive counts distinct customer IDs")
    void ingest_uniqueCustomers_countedCorrectly(){
        aggregator.ingest(evt(1L,101L,10L,"DEBIT","100.00","900.00"));
        aggregator.ingest(evt(2L,102L,10L,"CREDIT","50.00","950.00")); // same customer
        aggregator.ingest(evt(3L,103L,11L,"DEBIT","200.00","800.00")); // different customer
        Map<String,Object> s=aggregator.getSummary();
        assertThat((Integer)s.get("uniqueCustomersActive")).isEqualTo(2);
    }
    // ── getAccountBreakdown ───────────────────────────────────────────────────
    @Test @DisplayName("getAccountBreakdown: empty when no events ingested")
    void getAccountBreakdown_empty_whenNoEvents(){
        assertThat(aggregator.getAccountBreakdown()).isEmpty();
    }
    @Test @DisplayName("getAccountBreakdown: returns one entry per distinct account")
    void getAccountBreakdown_oneEntryPerAccount(){
        aggregator.ingest(evt(1L,101L,10L,"DEBIT","100.00","900.00"));
        aggregator.ingest(evt(2L,102L,11L,"CREDIT","200.00","1200.00"));
        aggregator.ingest(evt(3L,101L,10L,"DEBIT","50.00","850.00")); // same as first
        List<Map<String,Object>> bd=aggregator.getAccountBreakdown();
        assertThat(bd).hasSize(2);
    }
    @Test @DisplayName("getAccountBreakdown: sorted by totalTransactions descending")
    void getAccountBreakdown_sortedByActivityDescending(){
        aggregator.ingest(evt(1L,201L,10L,"DEBIT","100.00","900.00")); // acc 201: 1 tx
        aggregator.ingest(evt(2L,202L,11L,"CREDIT","200.00","1200.00")); // acc 202: 1 tx
        aggregator.ingest(evt(3L,202L,11L,"DEBIT","50.00","1150.00"));  // acc 202: 2 tx
        List<Map<String,Object>> bd=aggregator.getAccountBreakdown();
        assertThat(bd.get(0).get("accountId")).isEqualTo(202L); // most active first
    }
    // ── getRecentEvents ───────────────────────────────────────────────────────
    @Test @DisplayName("getRecentEvents: empty when no events ingested")
    void getRecentEvents_empty_whenNoEvents(){
        assertThat(aggregator.getRecentEvents()).isEmpty();
    }
    @Test @DisplayName("getRecentEvents: most recently ingested event appears first")
    void getRecentEvents_mostRecentFirst(){
        aggregator.ingest(evt(1L,101L,10L,"DEBIT","100.00","900.00"));
        aggregator.ingest(evt(2L,102L,11L,"CREDIT","500.00","1500.00"));
        List<Map<String,Object>> recent=aggregator.getRecentEvents();
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).get("transactionId")).isEqualTo(2L); // latest first
    }
    @Test @DisplayName("getRecentEvents: ring buffer capped at 50 events")
    void getRecentEvents_ringBuffer_cappedAt50(){
        for(int i=1;i<=60;i++) aggregator.ingest(evt(i,100L+i,10L,"DEBIT","10.00","990.00"));
        assertThat(aggregator.getRecentEvents()).hasSize(50);
    }
    // ── AnalyticsKafkaConsumer integration ───────────────────────────────────
    @Test @DisplayName("consume: valid Kafka JSON ingested into aggregator")
    void consumer_validJson_ingestsEvent(){
        String msg="{\"transactionId\":99,\"accountId\":201,\"customerId\":20,\"operation\":\"CREDIT\",\"amount\":\"750.00\",\"currency\":\"PHP\",\"beforeBalance\":\"500.00\",\"afterBalance\":\"1250.00\",\"referenceNo\":\"REF-99\",\"timestamp\":\"2026-09-28T10:00:00\"}";
        consumer.consume(msg);
        Map<String,Object> s=aggregator.getSummary();
        assertThat((Long)s.get("totalEventsProcessed")).isEqualTo(1L);
        assertThat((Long)s.get("totalCredits")).isEqualTo(1L);
    }
    @Test @DisplayName("consume: malformed JSON swallowed — aggregator remains unchanged")
    void consumer_malformedJson_aggregatorUnchanged(){
        assertThatNoException().isThrownBy(()->consumer.consume("NOT_JSON{{{"));
        assertThat((Long)aggregator.getSummary().get("totalEventsProcessed")).isZero();
    }
    @Test @DisplayName("consume: null event swallowed by ingest without NPE")
    void ingest_nullEvent_noException(){
        assertThatNoException().isThrownBy(()->aggregator.ingest(null));
        assertThat((Long)aggregator.getSummary().get("totalEventsProcessed")).isZero();
    }
    @Test @DisplayName("getSummary: totalVolumePhp is sum of debit + credit amounts")
    void getSummary_totalVolume_isSumOfDebitAndCredit(){
        aggregator.ingest(evt(1L,101L,10L,"DEBIT","300.00","700.00"));
        aggregator.ingest(evt(2L,102L,11L,"CREDIT","200.00","1200.00"));
        Map<String,Object> s=aggregator.getSummary();
        BigDecimal vol=(BigDecimal)s.get("totalVolumePhp");
        assertThat(vol).isEqualByComparingTo("500.00");
    }
    @Test @DisplayName("getSummary: uptimeSeconds is non-negative")
    void getSummary_uptimeSeconds_nonNegative(){
        long uptime=(Long)aggregator.getSummary().get("uptimeSeconds");
        assertThat(uptime).isGreaterThanOrEqualTo(0L);
    }
}
