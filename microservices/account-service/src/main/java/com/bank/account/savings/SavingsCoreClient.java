package com.bank.account.savings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import java.math.BigDecimal;
import java.util.*;
@Component
public class SavingsCoreClient {
 private final RestClient client;
 public record Breakdown(Map<String,BigDecimal> funds,Map<String,BigDecimal> reservations) {}
 public Breakdown breakdown(Long accountId) {
  try {
   var result=client.get().uri("/accounts/{id}/breakdown",accountId).retrieve().body(Breakdown.class);
   if(result==null || result.funds()==null || result.reservations()==null)throw new IllegalStateException();
   for(String key:List.of("accountBalance","reservedSavings","otherHolds","availableBalance"))Objects.requireNonNull(result.funds().get(key));
   return result;
  } catch(Exception e){throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Savings account breakdown unavailable. Please try again.");}
 }
 public SavingsCoreClient(@Value("${app.savings.core-url:http://t24-adapter:8090/internal/savings}") String url) {
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(1000);factory.setReadTimeout(5000);
  client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 @SuppressWarnings("unchecked") public Map<String,Object> apply(Object command) {
  Map<String,Object> result=client.post().uri("/operations").body(command).retrieve().body(Map.class);
  if(result==null || !"CONFIRMED".equals(result.get("status"))) throw new IllegalStateException("Unconfirmed core response");
  return result;
 }
 public Map<String,BigDecimal> balances(Long accountId) {
  try {
   @SuppressWarnings("unchecked") Map<String,Object> rows=client.get().uri("/accounts/{id}",accountId).retrieve().body(Map.class);
   if(rows==null) throw new IllegalStateException();
   var result=new HashMap<String,BigDecimal>(); rows.forEach((k,v)->result.put(k,new BigDecimal(v.toString())));return result;
  } catch(Exception e){throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Live savings balances unavailable");}
 }
 public Map<String,BigDecimal> funding(Long accountId,String goalId) {
  try {
   @SuppressWarnings("unchecked") Map<String,Object> rows=client.get().uri("/accounts/{id}/funding/{goal}",accountId,goalId).retrieve().body(Map.class);
   if(rows==null)throw new IllegalStateException();
   var result=new HashMap<String,BigDecimal>();
   for(String key:List.of("accountBalance","reservedSavings","otherHolds","availableBalance","goalSavedAmount"))result.put(key,new BigDecimal(rows.get(key).toString()));
   return result;
  } catch(Exception e){throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Available savings funds could not be checked. Please try again.");}
 }
}
