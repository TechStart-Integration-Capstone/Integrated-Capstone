package com.bank.account.savings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.savings.enabled",havingValue="true")
public class SavingsWorker {
 private static final Logger log=LoggerFactory.getLogger(SavingsWorker.class);
 private final SavingsService service;
 private final SavingsOperations operations;
 public SavingsWorker(SavingsService service,SavingsOperations operations){this.service=service;this.operations=operations;}
 @Scheduled(fixedDelayString="${app.savings.poll-ms:60000}",initialDelayString="${app.savings.poll-ms:60000}")
 public void poll() {
  for(var intent:service.jdbc.queryForList("SELECT TOP 25 * FROM app.SAVINGS_ACTIVITY WHERE status='PENDING' ORDER BY created_at")) {
   try{operations.execute(intent);}catch(Exception e){log.warn("Savings recovery pending for {}",intent.get("operation_id"));}
  }
  LocalDate today=LocalDate.now(ZoneId.of("Asia/Manila"));
  for(var schedule:service.jdbc.queryForList("SELECT TOP 25 s.*,g.customer_id,g.account_id,g.target_amount FROM app.SAVINGS_SCHEDULE s JOIN app.SAVINGS_GOAL g ON g.goal_id=s.goal_id WHERE s.enabled=1 AND s.next_due<=? ORDER BY s.next_due",today)) {
   try{run(schedule,today);}catch(Exception e){log.warn("Savings schedule deferred for {}",schedule.get("goal_id"));}
  }
 }
 @Scheduled(fixedDelayString="${app.savings.poll-ms:60000}",initialDelayString="${app.savings.poll-ms:60000}")
 public void notifyCompletedCircles() {
  for(var circle:service.jdbc.queryForList("SELECT circle_id FROM app.PINK_CIRCLE WHERE completion_notified=0")) {
   try{service.notifyCompletion(circle.get("circle_id").toString());}catch(Exception e){log.warn("Circle completion check deferred");}
  }
 }
 void run(Map<String,Object> schedule,LocalDate today) {
  String goal=schedule.get("goal_id").toString();LocalDate due=((java.sql.Date)schedule.get("next_due")).toLocalDate();
  String key="schedule:"+goal+":"+due;long customer=SavingsService.number(schedule,"customer_id");
  var existing=service.jdbc.queryForList("SELECT * FROM app.SAVINGS_ACTIVITY WHERE customer_id=? AND idempotency_key=?",customer,key);
  String status;
  if(!existing.isEmpty())status=operations.execute(existing.get(0)).get("status").toString();
  else {
   BigDecimal remaining=SavingsService.decimal(schedule,"target_amount").subtract(service.core.balances(SavingsService.number(schedule,"account_id")).getOrDefault(goal,BigDecimal.ZERO));
   if(remaining.signum()<=0)status="CONFIRMED";
   else status=operations.submitScheduled(customer,key,new SavingsRequests.Operation(SavingsService.number(schedule,"account_id"),"ALLOCATE",List.of(new SavingsRequests.Line(UUID.fromString(goal),remaining.min(SavingsService.decimal(schedule,"amount"))))),schedule).get("status").toString();
  }
  if(!"PENDING".equals(status)) {
   // Do not catch up missed paydays with several debits. Move to a future date.
   LocalDate next=nextDue(today,schedule.get("frequency").toString());
   service.jdbc.update("UPDATE app.SAVINGS_SCHEDULE SET next_due=? WHERE goal_id=? AND next_due=? AND amount=? AND frequency=? AND enabled=1",next,goal,due,schedule.get("amount"),schedule.get("frequency"));
  }
 }
 static LocalDate nextDue(LocalDate date,String frequency) {
  return switch(frequency) {
   case "WEEKLY" -> date.plusWeeks(1);
   case "MONTHLY" -> date.plusMonths(1);
   case "PAYDAY" -> date.getDayOfMonth()<15?date.withDayOfMonth(15):date.getDayOfMonth()<date.lengthOfMonth()?date.withDayOfMonth(date.lengthOfMonth()):date.plusMonths(1).withDayOfMonth(15);
   default -> throw new IllegalArgumentException("Unknown frequency");
  };
 }
}