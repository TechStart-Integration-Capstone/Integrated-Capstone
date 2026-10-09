package com.bank.t24.savings;
import com.bank.t24.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Service
public class CoreSavingsService {
 private final AccountRepository accounts;
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private final SavingsRiskClient risk;
 public CoreSavingsService(AccountRepository accounts,JdbcTemplate jdbc,ObjectMapper json,SavingsRiskClient risk){this.accounts=accounts;this.jdbc=jdbc;this.json=json;this.risk=risk;}
 @Transactional
 public Map<String,Object> apply(SavingsCommand command) {
  // Same account lock as transfers. Retry lookup MUST occur after acquiring it.
  var account=accounts.findByIdForUpdate(command.accountId()).orElseThrow(()->new ResponseStatusException(NOT_FOUND));
  if(!Objects.equals(account.getCustomerId(),command.customerId())) throw new ResponseStatusException(FORBIDDEN);
  String request=encode(command),id=command.operationId().toString();
  var existing=jdbc.queryForList("SELECT request_json,result_json FROM t24.SAVINGS_OPERATION WHERE operation_id=?",id);
  if(!existing.isEmpty()) {
   if(!existing.get(0).get("request_json").equals(request)) throw new ResponseStatusException(CONFLICT,"Operation key already used for different request");
   return decode(existing.get(0).get("result_json").toString());
  }
  if(!"ACTIVE".equals(account.getStatus()) || !"PHP".equals(account.getCurrency()) || !Set.of("SAVINGS","SAVINGS_ACCOUNT").contains(account.getAccountType())) throw new ResponseStatusException(UNPROCESSABLE_ENTITY,"An active PHP account is required");
  boolean release="RELEASE".equals(command.type());
  if(!release && !"ALLOCATE".equals(command.type())) throw new ResponseStatusException(BAD_REQUEST);
  var changes=new LinkedHashMap<String,BigDecimal>(); BigDecimal amount=BigDecimal.ZERO;
  for(var line:command.lines()) {
   String goal=line.goalId().toString();
   if(changes.containsKey(goal)) throw new ResponseStatusException(BAD_REQUEST,"Duplicate goal in split");
   if(line.amount().signum()<=0 || line.amount().scale()>2 || line.target().signum()<=0) throw new ResponseStatusException(BAD_REQUEST,"Invalid amount");
   var rows=jdbc.queryForList("SELECT account_id,reserved_amount FROM t24.SAVINGS_RESERVATION WHERE goal_id=?",goal);
   BigDecimal before=BigDecimal.ZERO;
   if(!rows.isEmpty()) {
    if(((Number)rows.get(0).get("account_id")).longValue()!=command.accountId()) throw new ResponseStatusException(CONFLICT,"Goal belongs to another account");
    before=(BigDecimal)rows.get(0).get("reserved_amount");
   }
   BigDecimal after=release?before.subtract(line.amount()):before.add(line.amount());
   if(after.signum()<0 || (!release && after.compareTo(line.target())>0)) throw new ResponseStatusException(UNPROCESSABLE_ENTITY,"Amount exceeds saved funds or goal target");
   changes.put(goal,after); amount=amount.add(line.amount());
  }
  accounts.protectReservations(account);
  if(!release && account.getAvailableBalance().compareTo(amount)<0) throw new ResponseStatusException(UNPROCESSABLE_ENTITY,"Insufficient available funds");
  if(release && account.getHeldBalance().compareTo(amount)<0) throw new ResponseStatusException(CONFLICT,"Reservation reconciliation required");
  BigDecimal score=risk.approve(account,amount,id), beforeHeld=account.getHeldBalance();
  account.setHeldBalance(release?beforeHeld.subtract(amount):beforeHeld.add(amount)); accounts.saveAndFlush(account);
  changes.forEach((goal,balance)->{
   if(jdbc.update("UPDATE t24.SAVINGS_RESERVATION SET reserved_amount=? WHERE goal_id=?",balance,goal)==0)
    jdbc.update("INSERT INTO t24.SAVINGS_RESERVATION(goal_id,account_id,reserved_amount) VALUES(?,?,?)",goal,command.accountId(),balance);
  });
  var result=new LinkedHashMap<String,Object>(); result.put("operationId",id);result.put("status","CONFIRMED");result.put("balances",changes);
  result.put("availableBalance",account.getAvailableBalance()); result.put("reservedChange",release?amount.negate():amount);
  LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
  jdbc.update("INSERT INTO t24.SAVINGS_OPERATION(operation_id,account_id,request_json,result_json,risk_score,created_at) VALUES(?,?,?,?,?,?)",id,command.accountId(),request,encode(result),score,now);
  var event=new LinkedHashMap<String,Object>(result);event.put("eventType",release?"savings.released":"savings.allocated");
  event.put("customerId",command.customerId());event.put("accountId",command.accountId());event.put("referenceNo",id);event.put("amount",amount);
  event.put("currency","PHP");event.put("heldBefore",beforeHeld);event.put("heldAfter",account.getHeldBalance());event.put("riskScore",score);event.put("occurredAt",now.toString()+"Z");
  // Held balance, reservation journal and outbox are one ACID transaction.
  jdbc.update("INSERT INTO app.OUTBOX_EVENT(aggregate_id,event_type,payload,status,created_date) VALUES(?,?,?,'PENDING',?)",id,event.get("eventType"),encode(event),now);
  return result;
 }
 @Transactional(readOnly=true)
 public Map<String,BigDecimal> balances(Long accountId) {
  var result=new LinkedHashMap<String,BigDecimal>();
  jdbc.query("SELECT goal_id,reserved_amount FROM t24.SAVINGS_RESERVATION WHERE account_id=?",rs->{result.put(rs.getString(1),rs.getBigDecimal(2));},accountId);
  return result;
 }
 @Transactional
 public Map<String,BigDecimal> funding(Long accountId,String goalId) {
  // Read account and reservations under the same lock used by contributions/transfers.
  var account=accounts.findByIdForUpdate(accountId).orElseThrow(()->new ResponseStatusException(NOT_FOUND));
  var reserved=balances(accountId);
  BigDecimal savings=reserved.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal effectiveHeld=account.getHeldBalance().max(accounts.recordedHolds(accountId)).max(savings);
  return Map.of("accountBalance",account.getCurrentBalance(),"reservedSavings",savings,
   "otherHolds",effectiveHeld.subtract(savings).max(BigDecimal.ZERO),
   "availableBalance",account.getCurrentBalance().subtract(effectiveHeld).max(BigDecimal.ZERO),
   "goalSavedAmount",reserved.getOrDefault(goalId,BigDecimal.ZERO));
 }
 @Transactional
 public Map<String,Object> breakdown(Long accountId) {
  // funding acquires the account lock; retain it through reading all allocations.
  var funds=funding(accountId,"");
  return Map.of("funds",funds,"reservations",balances(accountId));
 }
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 @SuppressWarnings("unchecked") private Map<String,Object> decode(String value){try{return json.readValue(value,Map.class);}catch(Exception e){throw new IllegalStateException(e);}}
}
