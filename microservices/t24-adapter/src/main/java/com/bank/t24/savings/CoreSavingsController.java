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
 public CoreSavingsController(CoreSavingsService service){this.service=service;}
 @PostMapping("/operations") public Map<String,Object> apply(@Valid @RequestBody SavingsCommand command){return service.apply(command);}
 @GetMapping("/accounts/{accountId}") public Map<String,BigDecimal> balances(@PathVariable Long accountId){return service.balances(accountId);}
}