package com.bank.account.savings;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/accounts/savings")
@ConditionalOnProperty(name="app.savings.enabled",havingValue="true")
public class SavingsController {
 private final SavingsService service;
 private final SavingsOperations operations;
 public SavingsController(SavingsService service,SavingsOperations operations){this.service=service;this.operations=operations;}
 @GetMapping public Map<String,Object> overview(@RequestHeader("X-Auth-Customer-Id") Long customer){return service.overview(customer);}
 @PostMapping("/goals") public Map<String,Object> create(@RequestHeader("X-Auth-Customer-Id") Long customer,@Valid @RequestBody SavingsRequests.Goal body){return service.createGoal(customer,body);}
 @PutMapping("/goals/{id}") public void edit(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id,@Valid @RequestBody SavingsRequests.Edit body){service.edit(customer,id.toString(),body);}
 @PutMapping("/goals/{id}/schedule") public void schedule(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id,@Valid @RequestBody SavingsRequests.Schedule body){service.schedule(customer,id.toString(),body);}
 @PostMapping("/operations") public ResponseEntity<Map<String,Object>> operation(@RequestHeader("X-Auth-Customer-Id") Long customer,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SavingsRequests.Operation body) {
  if(key.startsWith("schedule:"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Reserved idempotency-key prefix");
  var result=operations.submit(customer,key,body);
  int code="PENDING".equals(result.get("status"))?202:"REJECTED".equals(result.get("status"))?422:200;
  return ResponseEntity.status(code).body(result);
 }
 @GetMapping("/operations/{id}") public Map<String,Object> status(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id){return operations.status(customer,id.toString());}
 @GetMapping("/activity") public List<Map<String,Object>> activity(@RequestHeader("X-Auth-Customer-Id") Long customer){return operations.history(customer);}
 @GetMapping("/circles") public List<Map<String,Object>> circles(@RequestHeader("X-Auth-Customer-Id") Long customer){return service.circles(customer);}
 @PostMapping("/circles") public Map<String,Object> circle(@RequestHeader("X-Auth-Customer-Id") Long customer,@Valid @RequestBody SavingsRequests.Circle body){return service.createCircle(customer,body);}
 @PostMapping("/circles/{id}/invitations") public void invite(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id,@Valid @RequestBody SavingsRequests.Invite body){service.invite(customer,id.toString(),body);}
 @PostMapping("/circles/{id}/accept") public Map<String,Object> accept(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id,@Valid @RequestBody SavingsRequests.Accept body){return service.accept(customer,id.toString(),body);}
 @PutMapping("/circles/{id}/visibility") public void visibility(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id,@RequestBody SavingsRequests.Visibility body){service.visibility(customer,id.toString(),body.shareProgress());}
 @PostMapping("/circles/{id}/members/{member}/target") public void propose(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id,@PathVariable Long member,@Valid @RequestBody SavingsRequests.Target body){service.propose(customer,id.toString(),member,body.target());}
 @PostMapping("/circles/{id}/target/accept") public void approve(@RequestHeader("X-Auth-Customer-Id") Long customer,@PathVariable UUID id){service.approveTarget(customer,id.toString());}
}