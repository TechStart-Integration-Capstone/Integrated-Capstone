package com.bank.t24.savings;
import com.bank.t24.model.Account;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Component
public class SavingsRiskClient {
 private final RestClient client;
 private final JdbcTemplate jdbc;
 public SavingsRiskClient(@Value("${app.risk-engine.url:http://risk-engine:8000/score}") String url, JdbcTemplate jdbc) {
  var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(1000); factory.setReadTimeout(2000);
  client=RestClient.builder().baseUrl(url).requestFactory(factory).build(); this.jdbc=jdbc;
 }
 public BigDecimal approve(Account account, BigDecimal amount, String operation) {
  try {
   var body=new HashMap<String,Object>();
   body.put("accountId",account.getAccountId()); body.put("customerId",account.getCustomerId());
   body.put("amount",amount); body.put("currency","PHP"); body.put("transactionType","SAVINGS_RESERVATION");
   body.put("accountAgeHours",Math.max(0,Duration.between(account.getCreatedDate(),LocalDateTime.now(ZoneOffset.UTC)).toMinutes()/60.0));
   body.put("recentTxCount",jdbc.queryForObject("SELECT COUNT(*) FROM t24.SAVINGS_OPERATION WHERE account_id=? AND created_at>=?",Integer.class,account.getAccountId(),LocalDateTime.now(ZoneOffset.UTC).minusHours(1)));
   BigDecimal average=jdbc.queryForObject("SELECT AVG(ABS(CAST(JSON_VALUE(result_json, '$.reservedChange') AS DECIMAL(18,2)))) FROM t24.SAVINGS_OPERATION WHERE account_id=? AND created_at>=?",BigDecimal.class,account.getAccountId(),LocalDateTime.now(ZoneOffset.UTC).minusDays(30));
   if(average!=null && average.signum()>0)body.put("amountVsAvgRatio",amount.divide(average,5,java.math.RoundingMode.HALF_UP));
   @SuppressWarnings("unchecked") Map<String,Object> result=client.post().header("X-Correlation-ID",operation).body(body).retrieve().body(Map.class);
   if(result==null || !(result.get("score") instanceof Number)) throw new IllegalStateException("Missing score");
   BigDecimal score=new BigDecimal(result.get("score").toString());
   if(score.signum()<0 || score.compareTo(BigDecimal.ONE)>0) throw new IllegalStateException("Invalid score");
   if(!"APPROVE".equals(result.get("decision")) || score.compareTo(new BigDecimal("0.85"))>0)
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Savings request declined by risk checks");
   return score;
  } catch(ResponseStatusException e){throw e;}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Risk checks unavailable; no savings changed");}
 }
}