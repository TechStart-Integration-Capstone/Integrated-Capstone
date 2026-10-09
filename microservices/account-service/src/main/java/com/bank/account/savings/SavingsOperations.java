package com.bank.account.savings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Durable intent before RPC, core replay after timeouts, then local confirmation. */
@Service
public class SavingsOperations {
 private final SavingsService savings;
 private final JdbcTemplate jdbc;
 private final SavingsCoreClient core;
 private final ObjectMapper json;
 private final TransactionTemplate tx;
 public SavingsOperations(SavingsService savings,JdbcTemplate jdbc,SavingsCoreClient core,ObjectMapper json,PlatformTransactionManager manager) {
  this.savings=savings;this.jdbc=jdbc;this.core=core;this.json=json;this.tx=new TransactionTemplate(manager);
 }
 public Map<String,Object> submit(Long customer,String key,SavingsRequests.Operation request) {
  return submitChecked(customer,key,request,()->{});
 }
 Map<String,Object> submitScheduled(Long customer,String key,SavingsRequests.Operation request,Map<String,Object> snapshot) {
  return submitChecked(customer,key,request,()->{
   var schedules=jdbc.queryForList("SELECT * FROM app.SAVINGS_SCHEDULE WHERE goal_id=?",snapshot.get("goal_id"));
   if(schedules.isEmpty())throw new ResponseStatusException(CONFLICT,"Schedule removed");
   var current=schedules.get(0);
   if(!Boolean.TRUE.equals(current.get("enabled")) || !Objects.equals(current.get("next_due"),snapshot.get("next_due"))
      || !Objects.equals(current.get("frequency"),snapshot.get("frequency"))
      || SavingsService.decimal(current,"amount").compareTo(SavingsService.decimal(snapshot,"amount"))!=0)
    throw new ResponseStatusException(CONFLICT,"Schedule changed before execution");
  });
 }
 private Map<String,Object> submitChecked(Long customer,String key,SavingsRequests.Operation request,Runnable precondition) {
  if(key==null || !key.matches("[A-Za-z0-9:._-]{1,100}"))throw new ResponseStatusException(BAD_REQUEST,"Supply a valid Idempotency-Key");
  var lines=request.lines().stream().sorted(Comparator.comparing(l->l.goalId().toString()))
   .map(l->new SavingsRequests.Line(l.goalId(),l.amount().setScale(2))).toList();
  var normalized=new SavingsRequests.Operation(request.accountId(),request.type(),lines);
  String requestJson=encode(normalized);
  Map<String,Object> intent=tx.execute(status->{
   savings.lock(customer);
   var old=jdbc.queryForList("SELECT * FROM app.SAVINGS_ACTIVITY WHERE customer_id=? AND idempotency_key=?",customer,key);
   if(!old.isEmpty()) {
    var stored=decode(old.get(0).get("request_json").toString());
    if(!Objects.equals(stored.get("request"),requestJson))throw new ResponseStatusException(CONFLICT,"Idempotency-Key was used for different input");
    return old.get(0);
   }
   precondition.run();
   savings.noPending(customer);savings.account(customer,request.accountId());
   var commandLines=new ArrayList<Map<String,Object>>();var unique=new HashSet<UUID>();
   for(var line:lines) {
    if(!unique.add(line.goalId()))throw new ResponseStatusException(BAD_REQUEST,"Duplicate goal");
    var goal=savings.owned(customer,line.goalId().toString());
    if(SavingsService.number(goal,"account_id")!=request.accountId())throw new ResponseStatusException(BAD_REQUEST,"All goals must use the selected savings account");
    commandLines.add(Map.of("goalId",line.goalId().toString(),"amount",line.amount(),"target",goal.get("target_amount")));
   }
   String id=UUID.randomUUID().toString();
   var command=Map.of("operationId",id,"customerId",customer,"accountId",request.accountId(),"type",request.type(),"lines",commandLines);
   String payload=encode(Map.of("request",requestJson,"command",command));
   jdbc.update("INSERT INTO app.SAVINGS_ACTIVITY(operation_id,customer_id,account_id,idempotency_key,request_json,status) VALUES(?,?,?,?,?,'PENDING')",id,customer,request.accountId(),key,payload);
   return Map.<String,Object>of("operation_id",id,"customer_id",customer,"status","PENDING","request_json",payload);
  });
  return execute(intent);
 }
 public Map<String,Object> execute(Map<String,Object> intent) {
  if(!"PENDING".equals(intent.get("status")))return response(intent);
  String id=intent.get("operation_id").toString();Map<String,Object> result;String status;
  try {result=core.apply(decode(intent.get("request_json").toString()).get("command"));status="CONFIRMED";}
  catch(RestClientResponseException e) {
   // Only definitive validation/business failures may be marked rejected.
   if(!Set.of(400,403,404,409,422).contains(e.getStatusCode().value()))return Map.of("operationId",id,"status","PENDING");
   result=Map.of("operationId",id,"status","REJECTED","message","Core rejected the savings request","httpStatus",e.getStatusCode().value());status="REJECTED";
  } catch(Exception e){return Map.of("operationId",id,"status","PENDING");}
  String finalStatus=status,payload=encode(result);
  tx.executeWithoutResult(txStatus->{
   savings.lock(SavingsService.number(intent,"customer_id"));
   jdbc.update("UPDATE app.SAVINGS_ACTIVITY SET status=?,result_json=?,completed_at=? WHERE operation_id=? AND status='PENDING'",finalStatus,payload,LocalDateTime.now(ZoneOffset.UTC),id);
  });
  return response(jdbc.queryForMap("SELECT * FROM app.SAVINGS_ACTIVITY WHERE operation_id=?",id));
 }
 public Map<String,Object> status(Long customer,String id) {
  savings.customer(customer);var rows=jdbc.queryForList("SELECT * FROM app.SAVINGS_ACTIVITY WHERE customer_id=? AND operation_id=?",customer,id);
  if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND);return response(rows.get(0));
 }
 public List<Map<String,Object>> history(Long customer) {
  savings.customer(customer);
  return jdbc.queryForList("SELECT operation_id,account_id,idempotency_key,status,request_json,result_json,created_at,completed_at FROM app.SAVINGS_ACTIVITY WHERE customer_id=? ORDER BY created_at DESC OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY",customer);
 }
 private Map<String,Object> response(Map<String,Object> intent) {
  return intent.get("result_json")==null?Map.of("operationId",intent.get("operation_id"),"status",intent.get("status")):decode(intent.get("result_json").toString());
 }
 String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 @SuppressWarnings("unchecked") Map<String,Object> decode(String value){try{return json.readValue(value,Map.class);}catch(Exception e){throw new IllegalStateException(e);}}
}
