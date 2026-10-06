package com.bank.transaction.orchestrator.interest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/interest/eod")
@ConditionalOnProperty(name = "app.interest.enabled", havingValue = "true")
public class InterestEodController {
    private final InterestEodService service;
    public InterestEodController(InterestEodService service) { this.service = service; }

    @PostMapping("/accrue")
    public InterestEodService.Result accrue(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return execute(roles, () -> service.accrue(businessDate));
    }

    @PostMapping("/post")
    public InterestEodService.Result post(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return execute(roles, () -> service.postMonth(businessDate));
    }

    private InterestEodService.Result execute(String roles, Supplier<InterestEodService.Result> work) {
        if (roles == null || Arrays.stream(roles.split(",")).map(String::trim).noneMatch("ROLE_ADMIN"::equals))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
        try {
            return work.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }
}
