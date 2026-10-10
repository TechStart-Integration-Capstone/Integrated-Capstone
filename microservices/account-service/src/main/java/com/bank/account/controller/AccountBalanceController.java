package com.bank.account.controller;

import com.bank.account.savings.SavingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Core balance reads remain available when the optional Savings Hub is disabled. */
@RestController
public class AccountBalanceController {
    private final SavingsService service;

    public AccountBalanceController(SavingsService service) {
        this.service = service;
    }

    // Retain the existing URL for deployed web clients.
    @GetMapping("/api/v1/accounts/savings/balance-summary")
    public Map<String, Object> balanceSummary(@RequestHeader("X-Auth-Customer-Id") Long customer) {
        return service.balanceSummary(customer);
    }
}
