package com.bank.transaction.orchestrator.interest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.function.Supplier;
import jakarta.validation.Valid;
import java.util.List;

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

    @GetMapping("/missing")
    public List<LocalDate> missing(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return execute(roles, () -> service.missingDays(periodEnd));
    }

    @GetMapping("/overview")
    public InterestEodService.PeriodOverview overview(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return execute(roles, () -> service.overview(periodEnd));
    }

    @PostMapping("/resolve")
    public InterestAccrualStore.Proposal resolve(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestHeader(value = "X-Auth-Username", required = false) String actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @Valid @RequestBody InterestRecoveryRequest request) {
        return execute(roles, () -> {
            if (actor == null || actor.isBlank())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated administrator identity required");
            return service.prepareBackfill(businessDate, request, actor);
        });
    }

    @GetMapping("/backfills/{id}")
    public InterestAccrualStore.Proposal review(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
                                                @PathVariable java.util.UUID id) {
        return execute(roles, () -> service.backfillProposal(id));
    }

    @PostMapping("/backfills/{id}/approve")
    public InterestEodService.Result approve(@RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestHeader(value = "X-Auth-Username", required = false) String actor,
            @PathVariable java.util.UUID id, @Valid @RequestBody InterestRecoveryRequest.Approval request) {
        return execute(roles, () -> {
            if (actor == null || actor.isBlank())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated administrator identity required");
            return service.approveBackfill(id, request, actor);
        });
    }

    private <T> T execute(String roles, Supplier<T> work) {
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
