package com.bank.transaction.orchestrator.interest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Interest EOD Operations (Admin)", description = "End-of-Day interest accruals, monthly posting, and missing accrual resolution (ROLE_ADMIN)")
@RestController
@RequestMapping("/api/v1/interest/eod")
@ConditionalOnProperty(name = "app.interest.enabled", havingValue = "true")
public class InterestEodController {
    private final InterestEodService service;
    public InterestEodController(InterestEodService service) { this.service = service; }

    @Operation(summary = "Run daily interest accrual", description = "Calculates and records daily interest accruals for all active accounts on the given business date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accrual completed"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_ADMIN")
    })
    @PostMapping("/accrue")
    public InterestEodService.Result accrue(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Business date to accrue (YYYY-MM-DD)", example = "2026-10-08")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return execute(roles, () -> service.accrue(businessDate));
    }

    @Operation(summary = "Post monthly interest payouts", description = "Aggregates and posts accrued monthly interest payouts to customer accounts in Core Banking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Interest posted"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_ADMIN")
    })
    @PostMapping("/post")
    public InterestEodService.Result post(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Month business date (YYYY-MM-DD)", example = "2026-10-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return execute(roles, () -> service.postMonth(businessDate));
    }

    @Operation(summary = "List missing accrual dates", description = "Checks for missing interest accrual dates up to the specified period end date.")
    @GetMapping("/missing")
    public List<LocalDate> missing(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Period end date (YYYY-MM-DD)", example = "2026-10-08")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return execute(roles, () -> service.missingDays(periodEnd));
    }

    @Operation(summary = "Interest period overview", description = "Retrieves an aggregated summary of interest accruals and postings for the period.")
    @GetMapping("/overview")
    public InterestEodService.PeriodOverview overview(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Period end date (YYYY-MM-DD)", example = "2026-10-08")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return execute(roles, () -> service.overview(periodEnd));
    }

    @Operation(summary = "Prepare backfill proposal", description = "Prepares a four-eyes backfill proposal for a missed interest accrual date.")
    @PostMapping("/resolve")
    public InterestAccrualStore.Proposal resolve(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Username", required = false) String actor,
            @Parameter(description = "Business date for backfill (YYYY-MM-DD)", example = "2026-10-07")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @Valid @RequestBody InterestRecoveryRequest request) {
        return execute(roles, () -> {
            if (actor == null || actor.isBlank())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated administrator identity required");
            return service.prepareBackfill(businessDate, request, actor);
        });
    }

    @Operation(summary = "Review backfill proposal", description = "Fetches a backfill proposal by ID for review.")
    @GetMapping("/backfills/{id}")
    public InterestAccrualStore.Proposal review(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(description = "Proposal UUID") @PathVariable java.util.UUID id) {
        return execute(roles, () -> service.backfillProposal(id));
    }

    @Operation(summary = "Approve backfill proposal", description = "Approves and executes a backfill proposal by a second administrator (four-eyes principle).")
    @PostMapping("/backfills/{id}/approve")
    public InterestEodService.Result approve(
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @Parameter(hidden = true) @RequestHeader(value = "X-Auth-Username", required = false) String actor,
            @Parameter(description = "Proposal UUID") @PathVariable java.util.UUID id,
            @Valid @RequestBody InterestRecoveryRequest.Approval request) {
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
