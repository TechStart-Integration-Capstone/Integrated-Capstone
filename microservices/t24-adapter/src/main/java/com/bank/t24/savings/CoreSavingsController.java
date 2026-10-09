package com.bank.t24.savings;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.math.BigDecimal;
// Internal network only: /internal/** has no gateway route.
@RestController
@RequestMapping("/internal/savings")
public class CoreSavingsController {
 private final CoreSavingsService service;
 @GetMapping("/accounts/{accountId}/breakdown") public Map<String,Object> breakdown(@PathVariable Long accountId){return service.breakdown(accountId);}
 public CoreSavingsController(CoreSavingsService service){this.service=service;}
 @PostMapping("/operations") public Map<String,Object> apply(@Valid @RequestBody SavingsCommand command){return service.apply(command);}
 @GetMapping("/accounts/{accountId}") public Map<String,BigDecimal> balances(@PathVariable Long accountId){return service.balances(accountId);}
 @GetMapping("/accounts/{accountId}/funding/{goalId}") public Map<String,BigDecimal> funding(@PathVariable Long accountId,@PathVariable java.util.UUID goalId){return service.funding(accountId,goalId.toString());}
}
