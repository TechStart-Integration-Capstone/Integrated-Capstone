package com.bank.account.savings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringJUnitConfig(SavingsServiceTest.Config.class)
class SavingsServiceTest {
 @Configuration @EnableTransactionManagement
 @Import({SavingsService.class,SavingsOperations.class})
 static class Config {
  @Bean DataSource dataSource(){var d=new JdbcDataSource();d.setURL("jdbc:h2:mem:savings-app;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");return d;}
  @Bean JdbcTemplate jdbc(DataSource d){return new JdbcTemplate(d);}
  @Bean PlatformTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
  @Bean SavingsCoreClient core(){return mock(SavingsCoreClient.class);}
  @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
  @Bean SavingsWorker worker(SavingsService service,SavingsOperations operations,JdbcTemplate jdbc,SavingsCoreClient core){return new SavingsWorker(service,operations,jdbc,core);}
 }
 @Autowired SavingsService service;
 @Autowired SavingsOperations operations;
 @Autowired SavingsWorker worker;
 @Autowired SavingsCoreClient core;
 @Autowired JdbcTemplate jdbc;
 @Autowired DataSource dataSource;
 @BeforeEach void setup() {
  jdbc.execute("DROP ALL OBJECTS");new ResourceDatabasePopulator(new ClassPathResource("savings-schema.sql")).execute(dataSource);
  jdbc.update("INSERT INTO app.CUSTOMER VALUES(1,'levi','Levi','Dela Cruz','ACTIVE'),(2,'maria','Maria','Santos','ACTIVE'),(3,'frozen','Ana','Santos','FROZEN')");
  jdbc.update("INSERT INTO t24.ACCOUNT VALUES(11,1,'SAVINGS_ACCOUNT','ACTIVE','PHP'),(12,1,'CURRENT_ACCOUNT','ACTIVE','PHP'),(22,2,'SAVINGS_ACCOUNT','ACTIVE','PHP')");
  jdbc.execute("CREATE TABLE app.OUTBOX_EVENT(aggregate_id VARCHAR(40),event_type VARCHAR(50),payload VARCHAR,status VARCHAR(20),created_date TIMESTAMP)");
  reset(core);when(core.balances(anyLong())).thenReturn(Map.of());
 }
 SavingsRequests.Goal goal(long account){return new SavingsRequests.Goal(account,"Christmas","HOLIDAY",new BigDecimal("10000.00"),LocalDate.now().plusMonths(6));}
 String create(){return service.createGoal(1L,goal(11)).get("goalId").toString();}
 @Test void accountBreakdownIsOwnerOnlyAndSeparatesOwnAllocations() {
  String personal=create(),circle=create();
  // A linked circle goal represents only this customer's reservation.
  jdbc.update("INSERT INTO app.PINK_CIRCLE(circle_id,admin_customer_id,name,target_amount,target_date) VALUES('circle-test',1,'Trip',20000,?)",LocalDate.now().plusMonths(6));
  jdbc.update("UPDATE app.SAVINGS_GOAL SET circle_id=? WHERE goal_id=?","circle-test",circle);
  when(core.breakdown(11L)).thenReturn(new SavingsCoreClient.Breakdown(Map.of("accountBalance",new BigDecimal("20000"),"reservedSavings",new BigDecimal("11000"),"otherHolds",BigDecimal.ZERO,"availableBalance",new BigDecimal("9000")),Map.of(personal,new BigDecimal("6000"),circle,new BigDecimal("5000"))));
  var result=service.breakdown(1L,11L);
  assertThat((BigDecimal)result.get("personalReserved")).isEqualByComparingTo("6000");
  assertThat((BigDecimal)result.get("circleReserved")).isEqualByComparingTo("5000");
  assertThat((List<?>)result.get("allocations")).hasSize(2);
  assertThatThrownBy(()->service.breakdown(2L,11L)).isInstanceOf(ResponseStatusException.class);
  verify(core,times(1)).breakdown(11L);
 }
 SavingsRequests.Operation operation(String id){return new SavingsRequests.Operation(11L,"ALLOCATE",List.of(new SavingsRequests.Line(UUID.fromString(id),new BigDecimal("500.00"))));}
 @Test void onlyOwnSavingsAccountCanCreateGoalAndStartsAtZero() {
  String id=create();assertThat(service.overview(1L).get("goalCount")).isEqualTo(1);
  assertThat(service.overview(1L).get("personalTotal")).isEqualTo(BigDecimal.ZERO);
  assertThatThrownBy(()->service.createGoal(1L,goal(12))).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.createGoal(1L,goal(22))).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.edit(2L,id,new SavingsRequests.Edit("Changed",BigDecimal.TEN,LocalDate.now()))).isInstanceOf(ResponseStatusException.class);
  verify(core,never()).apply(any());
 }
 @Test void fundingUsesLesserOfAvailableFundsAndRemainingPersonalOrCircleTarget() {
  String goal=create();
  var snapshot=new HashMap<String,BigDecimal>(Map.of("accountBalance",new BigDecimal("20000"),"reservedSavings",new BigDecimal("11000"),"otherHolds",BigDecimal.ZERO,"availableBalance",new BigDecimal("9000"),"goalSavedAmount",new BigDecimal("5000")));
  when(core.funding(11L,goal)).thenReturn(snapshot);
  assertThat(service.funding(1L,goal).get("maximumContribution")).isEqualTo(new BigDecimal("5000.00"));
  snapshot.put("availableBalance",new BigDecimal("3000.009"));
  assertThat(service.funding(1L,goal).get("maximumContribution")).isEqualTo(new BigDecimal("3000.00"));
  snapshot.put("availableBalance",BigDecimal.ZERO);
  assertThat(service.funding(1L,goal).get("maximumContribution")).isEqualTo(new BigDecimal("0.00"));
  assertThatThrownBy(()->service.funding(2L,goal)).isInstanceOf(ResponseStatusException.class);
  verify(core,never()).funding(22L,goal);
  var circle=service.createCircle(1L,new SavingsRequests.Circle(goal(11),new BigDecimal("4000"),false));
  String own=circle.get("goalId").toString();snapshot.put("availableBalance",new BigDecimal("9000"));snapshot.put("goalSavedAmount",new BigDecimal("1000"));
  when(core.funding(11L,own)).thenReturn(snapshot);
  assertThat(service.funding(1L,own).get("maximumContribution")).isEqualTo(new BigDecimal("3000.00"));
 }
 @Test void circleInvitationsRequireRegisteredUserAndAcceptance() {
  var result=service.createCircle(1L,new SavingsRequests.Circle(goal(11),new BigDecimal("4000"),false));String circle=result.get("circleId").toString();
  assertThatThrownBy(()->service.invite(1L,circle,new SavingsRequests.Invite("outsider",BigDecimal.TEN))).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.invite(2L,circle,new SavingsRequests.Invite("maria",BigDecimal.TEN))).isInstanceOf(ResponseStatusException.class);
  service.invite(1L,circle,new SavingsRequests.Invite("maria",new BigDecimal("6000")));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.SAVINGS_GOAL WHERE customer_id=2",Integer.class)).isZero();
  assertThatThrownBy(()->service.accept(2L,circle,new SavingsRequests.Accept(11L,true))).isInstanceOf(ResponseStatusException.class);
  service.accept(2L,circle,new SavingsRequests.Accept(22L,false));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.SAVINGS_GOAL WHERE customer_id=2",Integer.class)).isEqualTo(1);
  service.accept(2L,circle,new SavingsRequests.Accept(22L,false));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.SAVINGS_GOAL WHERE customer_id=2",Integer.class)).isEqualTo(1);
  service.propose(1L,circle,2L,new BigDecimal("5000"));
  assertThat(jdbc.queryForObject("SELECT target_amount FROM app.SAVINGS_GOAL WHERE customer_id=2",BigDecimal.class)).isEqualByComparingTo("6000");
  service.approveTarget(2L,circle);assertThat(jdbc.queryForObject("SELECT target_amount FROM app.SAVINGS_GOAL WHERE customer_id=2",BigDecimal.class)).isEqualByComparingTo("5000");
 }
 @Test void hiddenContributionsDoNotExposeAccountOrGoalIdentifiers() {
  var result=service.createCircle(1L,new SavingsRequests.Circle(goal(11),new BigDecimal("4000"),false));String circle=result.get("circleId").toString();
  service.invite(1L,circle,new SavingsRequests.Invite("maria",new BigDecimal("6000")));String maria=service.accept(2L,circle,new SavingsRequests.Accept(22L,false)).get("goalId").toString();
  when(core.balances(22L)).thenReturn(Map.of(maria,new BigDecimal("500")));
  var view=service.circles(1L).get(0);assertThat(view.get("savedAmount")).isEqualTo(new BigDecimal("500"));
  @SuppressWarnings("unchecked") var members=(List<Map<String,Object>>)view.get("members");var other=members.stream().filter(m->SavingsService.number(m,"customer_id")==2).findFirst().orElseThrow();
  assertThat(other).doesNotContainKeys("savedAmount","account_id","goal_id");
  service.visibility(2L,circle,true);assertThat(service.circles(1L).toString()).contains("savedAmount=500");
 }
 @Test void timeoutPersistsIntentThenRetryUsesSameCoreCommand() {
  String goal=create();when(core.apply(any())).thenThrow(new RuntimeException("timeout"));
  var pending=operations.submit(1L,"key-1",operation(goal));assertThat(pending.get("status")).isEqualTo("PENDING");
  assertThatThrownBy(()->service.edit(1L,goal,new SavingsRequests.Edit("Changed",BigDecimal.TEN,LocalDate.now()))).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->operations.submit(1L,"key-2",operation(goal))).isInstanceOf(ResponseStatusException.class);
  doReturn(Map.of("status","CONFIRMED","operationId",pending.get("operationId"))).when(core).apply(any());
  assertThat(operations.submit(1L,"key-1",operation(goal)).get("status")).isEqualTo("CONFIRMED");
  operations.submit(1L,"key-1",operation(goal));verify(core,times(2)).apply(any());
  var changed=new SavingsRequests.Operation(11L,"RELEASE",operation(goal).lines());
  assertThatThrownBy(()->operations.submit(1L,"key-1",changed)).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->operations.status(2L,pending.get("operationId").toString())).isInstanceOf(ResponseStatusException.class);
 }
 @Test void scheduleCalendarHandlesPaydaysAndMonthEnd() {
  assertThat(SavingsWorker.nextDue(LocalDate.of(2028,2,15),"PAYDAY")).isEqualTo(LocalDate.of(2028,2,29));
  assertThat(SavingsWorker.nextDue(LocalDate.of(2028,2,29),"PAYDAY")).isEqualTo(LocalDate.of(2028,3,15));
  assertThat(SavingsWorker.nextDue(LocalDate.of(2027,1,31),"MONTHLY")).isEqualTo(LocalDate.of(2027,2,28));
 }
 @Test void overviewStreakCountsOnlyConsecutiveFinalScheduledAttempts() {
  String goal=create();
  String[] statuses={"CONFIRMED","REJECTED","CONFIRMED","CONFIRMED","PENDING"};
  for(int i=0;i<statuses.length;i++) {
   jdbc.update("INSERT INTO app.SAVINGS_ACTIVITY(operation_id,customer_id,account_id,idempotency_key,request_json,status,created_at) VALUES(?,1,11,?,'{}',?,?)",
    UUID.randomUUID().toString(),"schedule:"+goal+":2026-10-"+(10+i),statuses[i],java.time.LocalDateTime.of(2026,10,10+i,0,0));
  }
  jdbc.update("INSERT INTO app.SAVINGS_ACTIVITY(operation_id,customer_id,account_id,idempotency_key,request_json,status,created_at) VALUES(?,1,11,'manual-rejected','{}','REJECTED',?)",
   UUID.randomUUID().toString(),java.time.LocalDateTime.of(2026,10,20,0,0));
  var overview=service.overview(1L);
  assertThat(overview.get("customerId")).isEqualTo(1L);
  @SuppressWarnings("unchecked") var goals=(List<Map<String,Object>>)overview.get("goals");
  assertThat(goals.get(0).get("streak")).isEqualTo(2);
  assertThat(operations.history(1L).get(0)).containsKey("idempotency_key");
 }
 @Test void completionNotificationEmitsOnceAndOnlyWhenFunded() {
  var result=service.createCircle(1L,new SavingsRequests.Circle(goal(11),new BigDecimal("10000"),false));String circle=result.get("circleId").toString();
  service.notifyCompletion(circle);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.OUTBOX_EVENT",Integer.class)).isZero();
  when(core.balances(11L)).thenReturn(Map.of(result.get("goalId").toString(),new BigDecimal("10000")));
  service.notifyCompletion(circle);service.notifyCompletion(circle);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.OUTBOX_EVENT",Integer.class)).isEqualTo(1);
 }

 @Test void changedScheduleIsNotExecutedFromStaleSnapshot() {
  String goal=create();var due=LocalDate.now().plusDays(1);
  service.schedule(1L,goal,new SavingsRequests.Schedule(new BigDecimal("500"),"WEEKLY",due,true));
  var snapshot=jdbc.queryForMap("SELECT * FROM app.SAVINGS_SCHEDULE WHERE goal_id=?",goal);
  service.schedule(1L,goal,new SavingsRequests.Schedule(new BigDecimal("500"),"WEEKLY",due,false));
  assertThatThrownBy(()->operations.submitScheduled(1L,"schedule:"+goal+":"+due,operation(goal),snapshot)).isInstanceOf(ResponseStatusException.class);
  verify(core,never()).apply(any());
 }

 @Test void workerRecoversPendingIntentWithTransactionalServiceProxy() {
  assertThat(org.springframework.aop.support.AopUtils.isAopProxy(service)).isTrue();
  String goal=create();when(core.apply(any())).thenThrow(new RuntimeException("timeout"));
  var pending=operations.submit(1L,"worker-recovery",operation(goal));
  doReturn(Map.of("status","CONFIRMED","operationId",pending.get("operationId"))).when(core).apply(any());
  worker.poll();worker.poll();
  assertThat(operations.status(1L,pending.get("operationId").toString()).get("status")).isEqualTo("CONFIRMED");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.SAVINGS_ACTIVITY",Integer.class)).isEqualTo(1);
  verify(core,times(2)).apply(any());
  service.edit(1L,goal,new SavingsRequests.Edit("Recovered goal",new BigDecimal("10000"),LocalDate.now().plusMonths(6)));
 }

 @Test void workerExecutesDueScheduleAndAdvancesItWithTransactionalServiceProxy() {
  String goal=create();LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Manila"));
  service.schedule(1L,goal,new SavingsRequests.Schedule(new BigDecimal("500"),"WEEKLY",today,true));
  when(core.apply(any())).thenAnswer(call->{@SuppressWarnings("unchecked") var command=(Map<String,Object>)call.getArgument(0);return Map.of("status","CONFIRMED","operationId",command.get("operationId"));});
  worker.poll();worker.poll();
  verify(core,times(1)).apply(any());
  assertThat(jdbc.queryForObject("SELECT next_due FROM app.SAVINGS_SCHEDULE WHERE goal_id=?",java.sql.Date.class,goal).toLocalDate()).isEqualTo(today.plusWeeks(1));
  assertThat(operations.history(1L).get(0).get("idempotency_key")).isEqualTo("schedule:"+goal+":"+today);
 }

 @Test void workerNotifiesCompletedCircleWithTransactionalServiceProxy() {
  var circle=service.createCircle(1L,new SavingsRequests.Circle(goal(11),new BigDecimal("10000"),false));
  when(core.balances(11L)).thenReturn(Map.of(circle.get("goalId").toString(),new BigDecimal("10000")));
  worker.notifyCompletedCircles();worker.notifyCompletedCircles();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.OUTBOX_EVENT",Integer.class)).isEqualTo(1);
 }

}
