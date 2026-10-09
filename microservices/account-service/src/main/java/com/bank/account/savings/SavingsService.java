package com.bank.account.savings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class SavingsService {
 private final JdbcTemplate jdbc;
 private final SavingsCoreClient core;
 private final com.fasterxml.jackson.databind.ObjectMapper json;
 public SavingsService(JdbcTemplate jdbc,SavingsCoreClient core,com.fasterxml.jackson.databind.ObjectMapper json){this.jdbc=jdbc;this.core=core;this.json=json;}
 public void customer(Long customer) {
  if(customer==null || customer<=0) throw new ResponseStatusException(UNAUTHORIZED);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM app.CUSTOMER WHERE customer_id=? AND status='ACTIVE'",Integer.class,customer)!=1)
   throw new ResponseStatusException(FORBIDDEN,"An active PayPink customer is required");
 }
 void lock(Long customer) {
  customer(customer);
  jdbc.queryForObject("SELECT customer_id FROM app.CUSTOMER WITH (UPDLOCK, ROWLOCK) WHERE customer_id=?",Long.class,customer);
 }
 void noPending(Long customer) {
  if(jdbc.queryForObject("SELECT COUNT(*) FROM app.SAVINGS_ACTIVITY WHERE customer_id=? AND status='PENDING'",Integer.class,customer)>0)
   throw new ResponseStatusException(CONFLICT,"A savings request is pending; retry it before changing goals");
 }
 void account(Long customer,Long account) {
  if(jdbc.queryForObject("SELECT COUNT(*) FROM t24.ACCOUNT WHERE account_id=? AND customer_id=? AND status='ACTIVE' AND currency='PHP' AND account_type IN ('SAVINGS','SAVINGS_ACCOUNT')",Integer.class,account,customer)!=1)
   throw new ResponseStatusException(FORBIDDEN,"Use your own active PHP savings account");
 }
 Map<String,Object> owned(Long customer,String id) {
  var rows=jdbc.queryForList("SELECT * FROM app.SAVINGS_GOAL WHERE goal_id=? AND customer_id=?",id,customer);
  if(rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Savings goal not found");return rows.get(0);
 }
 static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 static BigDecimal decimal(Map<String,Object> row,String key){return (BigDecimal)row.get(key);}
 BigDecimal saved(Map<String,Object> goal){return core.balances(number(goal,"account_id")).getOrDefault(goal.get("goal_id").toString(),BigDecimal.ZERO);}
 static String name(String value){String n=value.trim();if(n.isEmpty())throw new ResponseStatusException(BAD_REQUEST,"Name is required");return n;}
 String insertGoal(Long customer,SavingsRequests.Goal goal,String circle,BigDecimal target) {
  account(customer,goal.accountId());String id=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO app.SAVINGS_GOAL(goal_id,customer_id,account_id,circle_id,name,category,target_amount,target_date) VALUES(?,?,?,?,?,?,?,?)",id,customer,goal.accountId(),circle,name(goal.name()),goal.category(),target,goal.targetDate());return id;
 }
 @Transactional public Map<String,Object> createGoal(Long customer,SavingsRequests.Goal goal) {
  lock(customer);String id=insertGoal(customer,goal,null,goal.target());return Map.of("goalId",id,"savedAmount",BigDecimal.ZERO);
 }
 public Map<String,Object> overview(Long customer) {
  customer(customer);var goals=jdbc.queryForList("SELECT * FROM app.SAVINGS_GOAL WHERE customer_id=? ORDER BY created_at,goal_id",customer);
  var accounts=new HashMap<Long,Map<String,BigDecimal>>();BigDecimal personal=BigDecimal.ZERO,group=BigDecimal.ZERO;int count=0,completed=0;
  for(var goal:goals) {
   BigDecimal amount=accounts.computeIfAbsent(number(goal,"account_id"),core::balances).getOrDefault(goal.get("goal_id").toString(),BigDecimal.ZERO);
   goal.put("savedAmount",amount);
   // Only finalized scheduled attempts count; manual top-ups and pending intents do not.
   var attempts=jdbc.queryForList("SELECT status FROM app.SAVINGS_ACTIVITY WHERE customer_id=? AND idempotency_key LIKE ? AND status<>'PENDING' ORDER BY created_at DESC,operation_id DESC",customer,"schedule:"+goal.get("goal_id")+":%");
   int streak=0;
   for(var attempt:attempts){if(!"CONFIRMED".equals(attempt.get("status")))break;streak++;}
   goal.put("streak",streak);
   goal.put("schedule",jdbc.queryForList("SELECT amount,frequency,next_due,enabled FROM app.SAVINGS_SCHEDULE WHERE goal_id=?",goal.get("goal_id")));
   if(goal.get("circle_id")==null){personal=personal.add(amount);count++;if(amount.compareTo(decimal(goal,"target_amount"))>=0)completed++;}else group=group.add(amount);
  }
  return Map.of("customerId",customer,"goals",goals,"personalTotal",personal,"groupTotal",group,"goalCount",count,"completedGoals",completed);
 }
 @Transactional public void edit(Long customer,String id,SavingsRequests.Edit edit) {
  lock(customer);noPending(customer);var goal=owned(customer,id);
  if(goal.get("circle_id")!=null)throw new ResponseStatusException(CONFLICT,"Circle targets require a member-approved proposal");
  if(edit.target().compareTo(saved(goal))<0)throw new ResponseStatusException(CONFLICT,"Target is below reserved savings");
  jdbc.update("UPDATE app.SAVINGS_GOAL SET name=?,target_amount=?,target_date=? WHERE goal_id=?",name(edit.name()),edit.target(),edit.targetDate(),id);
 }
 @Transactional public void schedule(Long customer,String id,SavingsRequests.Schedule schedule) {
  lock(customer);noPending(customer);owned(customer,id);
  if("PAYDAY".equals(schedule.frequency()) && schedule.nextDue().getDayOfMonth()!=15 && schedule.nextDue().getDayOfMonth()!=schedule.nextDue().lengthOfMonth())
   throw new ResponseStatusException(BAD_REQUEST,"Paydays are the 15th and month-end");
  if(jdbc.update("UPDATE app.SAVINGS_SCHEDULE SET amount=?,frequency=?,next_due=?,enabled=? WHERE goal_id=?",schedule.amount(),schedule.frequency(),schedule.nextDue(),schedule.enabled(),id)==0)
   jdbc.update("INSERT INTO app.SAVINGS_SCHEDULE(goal_id,amount,frequency,next_due,enabled) VALUES(?,?,?,?,?)",id,schedule.amount(),schedule.frequency(),schedule.nextDue(),schedule.enabled());
 }
 @Transactional public Map<String,Object> createCircle(Long customer,SavingsRequests.Circle request) {
  lock(customer);if(request.myTarget().compareTo(request.goal().target())>0)throw new ResponseStatusException(BAD_REQUEST,"Personal target exceeds shared target");
  account(customer,request.goal().accountId());String id=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO app.PINK_CIRCLE(circle_id,admin_customer_id,name,target_amount,target_date) VALUES(?,?,?,?,?)",id,customer,name(request.goal().name()),request.goal().target(),request.goal().targetDate());
  String goal=insertGoal(customer,request.goal(),id,request.myTarget());
  jdbc.update("INSERT INTO app.PINK_CIRCLE_MEMBER(circle_id,customer_id,goal_id,status,target_amount,share_progress) VALUES(?,?,?,'ACTIVE',?,?)",id,customer,goal,request.myTarget(),request.shareProgress());
  return Map.of("circleId",id,"goalId",goal);
 }
 Map<String,Object> circle(String id) {
  var rows=jdbc.queryForList("SELECT * FROM app.PINK_CIRCLE WITH (UPDLOCK, ROWLOCK) WHERE circle_id=?",id);
  if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND);return rows.get(0);
 }
 void admin(Long customer,Map<String,Object> circle){if(number(circle,"admin_customer_id")!=customer)throw new ResponseStatusException(FORBIDDEN,"Circle admin required");}
 void targetFits(String circle,Long member,BigDecimal target,BigDecimal total) {
  // Outstanding proposals reserve target capacity as well, preventing overbooking.
  BigDecimal used=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN proposed_target>target_amount THEN proposed_target ELSE target_amount END),0) FROM app.PINK_CIRCLE_MEMBER WHERE circle_id=? AND customer_id<>?",BigDecimal.class,circle,member);
  if(used.add(target).compareTo(total)>0)throw new ResponseStatusException(CONFLICT,"Member targets exceed the shared target");
 }
 @Transactional public void invite(Long customer,String id,SavingsRequests.Invite request) {
  lock(customer);var circle=circle(id);admin(customer,circle);
  var users=jdbc.queryForList("SELECT customer_id FROM app.CUSTOMER WHERE username=? AND status='ACTIVE'",request.username().trim());
  if(users.size()!=1)throw new ResponseStatusException(NOT_FOUND,"Active PayPink user not found");
  Long member=number(users.get(0),"customer_id");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM app.PINK_CIRCLE_MEMBER WHERE circle_id=? AND customer_id=?",Integer.class,id,member)>0)throw new ResponseStatusException(CONFLICT,"User already invited or joined");
  targetFits(id,member,request.target(),decimal(circle,"target_amount"));
  jdbc.update("INSERT INTO app.PINK_CIRCLE_MEMBER(circle_id,customer_id,status,target_amount,share_progress) VALUES(?,?,'INVITED',?,0)",id,member,request.target());
 }
 @Transactional public Map<String,Object> accept(Long customer,String id,SavingsRequests.Accept request) {
  lock(customer);var circle=circle(id);account(customer,request.accountId());
  var members=jdbc.queryForList("SELECT * FROM app.PINK_CIRCLE_MEMBER WHERE circle_id=? AND customer_id=?",id,customer);
  if(members.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Invitation not found");var member=members.get(0);
  if("ACTIVE".equals(member.get("status")))return Map.of("goalId",member.get("goal_id"));
  var goal=new SavingsRequests.Goal(request.accountId(),circle.get("name").toString(),"OTHER",decimal(member,"target_amount"),((java.sql.Date)circle.get("target_date")).toLocalDate());
  String goalId=insertGoal(customer,goal,id,goal.target());
  jdbc.update("UPDATE app.PINK_CIRCLE_MEMBER SET goal_id=?,status='ACTIVE',share_progress=? WHERE circle_id=? AND customer_id=?",goalId,request.shareProgress(),id,customer);
  return Map.of("goalId",goalId);
 }
 @Transactional public void visibility(Long customer,String id,boolean visible) {
  lock(customer);
  if(jdbc.update("UPDATE app.PINK_CIRCLE_MEMBER SET share_progress=? WHERE circle_id=? AND customer_id=? AND status='ACTIVE'",visible,id,customer)!=1)throw new ResponseStatusException(NOT_FOUND);
 }
 @Transactional public void propose(Long customer,String id,Long member,BigDecimal target) {
  lock(customer);var circle=circle(id);admin(customer,circle);targetFits(id,member,target,decimal(circle,"target_amount"));
  if(jdbc.update("UPDATE app.PINK_CIRCLE_MEMBER SET proposed_target=? WHERE circle_id=? AND customer_id=? AND status='ACTIVE'",target,id,member)!=1)throw new ResponseStatusException(NOT_FOUND);
 }
 @Transactional public void approveTarget(Long customer,String id) {
  lock(customer);noPending(customer);var circle=circle(id);
  var members=jdbc.queryForList("SELECT * FROM app.PINK_CIRCLE_MEMBER WHERE circle_id=? AND customer_id=? AND status='ACTIVE'",id,customer);
  if(members.isEmpty() || members.get(0).get("proposed_target")==null)throw new ResponseStatusException(NOT_FOUND,"No pending proposal");
  var member=members.get(0);BigDecimal target=decimal(member,"proposed_target");var goal=owned(customer,member.get("goal_id").toString());
  if(target.compareTo(saved(goal))<0)throw new ResponseStatusException(CONFLICT,"Release savings before reducing target below saved amount");
  targetFits(id,customer,target,decimal(circle,"target_amount"));
  jdbc.update("UPDATE app.SAVINGS_GOAL SET target_amount=? WHERE goal_id=?",target,goal.get("goal_id"));
  jdbc.update("UPDATE app.PINK_CIRCLE_MEMBER SET target_amount=?,proposed_target=NULL WHERE circle_id=? AND customer_id=?",target,id,customer);
 }
 public List<Map<String,Object>> circles(Long customer) {
  customer(customer);
  var circles=jdbc.queryForList("SELECT c.*,m.status AS membership_status FROM app.PINK_CIRCLE c JOIN app.PINK_CIRCLE_MEMBER m ON m.circle_id=c.circle_id WHERE m.customer_id=?",customer);
  var balances=new HashMap<Long,Map<String,BigDecimal>>();
  for(var circle:circles) {
   if(!"ACTIVE".equals(circle.get("membership_status")))continue;
   var members=jdbc.queryForList("SELECT m.customer_id,m.goal_id,m.status,m.target_amount,m.proposed_target,m.share_progress,c.first_name,c.last_name,g.account_id FROM app.PINK_CIRCLE_MEMBER m JOIN app.CUSTOMER c ON c.customer_id=m.customer_id LEFT JOIN app.SAVINGS_GOAL g ON g.goal_id=m.goal_id WHERE m.circle_id=?",circle.get("circle_id"));
   BigDecimal total=BigDecimal.ZERO;
   for(var member:members) {
    BigDecimal amount=member.get("goal_id")==null?BigDecimal.ZERO:balances.computeIfAbsent(number(member,"account_id"),core::balances).getOrDefault(member.get("goal_id").toString(),BigDecimal.ZERO);
    total=total.add(amount);
    boolean own=number(member,"customer_id")==customer;
    if(own || Boolean.TRUE.equals(member.get("share_progress")))member.put("savedAmount",amount);
    if(!own){member.remove("goal_id");member.remove("proposed_target");}
    member.remove("account_id");
   }
   circle.put("members",members);circle.put("savedAmount",total);circle.put("completed",total.compareTo(decimal(circle,"target_amount"))>=0);
  }
  return circles;
 }
 @Transactional public void notifyCompletion(String id) {
  var circle=circle(id);
  if(Boolean.TRUE.equals(circle.get("completion_notified")))return;
  var goals=jdbc.queryForList("SELECT goal_id,account_id,customer_id FROM app.SAVINGS_GOAL WHERE circle_id=?",id);
  var balances=new HashMap<Long,Map<String,BigDecimal>>();BigDecimal total=BigDecimal.ZERO;
  for(var goal:goals) total=total.add(balances.computeIfAbsent(number(goal,"account_id"),core::balances).getOrDefault(goal.get("goal_id").toString(),BigDecimal.ZERO));
  if(total.compareTo(decimal(circle,"target_amount"))<0)return;
  jdbc.update("UPDATE app.PINK_CIRCLE SET completion_notified=1 WHERE circle_id=?",id);
  for(var goal:goals) {
   String reference=UUID.randomUUID().toString();
   var event=Map.of("eventType","savings.circle_completed","customerId",number(goal,"customer_id"),"accountId",number(goal,"account_id"),"referenceNo",reference,"circleId",id);
   try {jdbc.update("INSERT INTO app.OUTBOX_EVENT(aggregate_id,event_type,payload,status,created_date) VALUES(?,'savings.circle_completed',?,'PENDING',?)",id,json.writeValueAsString(event),LocalDateTime.now(ZoneOffset.UTC));}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
  }
 }

}
