package com.bank.t24.controller;

import com.bank.t24.dto.T24HoldRequest;
import com.bank.t24.dto.T24HoldResponse;
import com.bank.t24.dto.T24ReleaseRequest;
import com.bank.t24.service.T24HoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "T24 Balance Holds", description = "Atomic balance lock and release operations (t24.LOCKED_AMOUNT)")
@RestController
@RequestMapping("/api/v1/t24/holds")
public class T24HoldController {

    private static final Logger log = LoggerFactory.getLogger(T24HoldController.class);

    private final T24HoldService holdService;

    public T24HoldController(T24HoldService holdService) {
        this.holdService = holdService;
    }

    @Operation(summary = "Place balance hold (lock)", description = "Atomically locks funds in t24.LOCKED_AMOUNT against an account, decreasing available balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hold placed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid hold parameters"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds or account inactive")
    })
    @PostMapping(value = "/lock", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> placeHold(@Valid @RequestBody T24HoldRequest request) {
        try {
            T24HoldResponse response = holdService.placeHold(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("[t24-hold-ctrl] Bad hold request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("[t24-hold-ctrl] Hold rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[t24-hold-ctrl] Internal error placing hold", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @Operation(summary = "Release balance hold", description = "Releases an active hold in t24.LOCKED_AMOUNT, restoring funds to available balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hold released successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid release parameters"),
            @ApiResponse(responseCode = "422", description = "Hold not found or already released")
    })
    @PostMapping(value = "/release", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> releaseHold(@Valid @RequestBody T24ReleaseRequest request) {
        try {
            T24HoldResponse response = holdService.releaseHold(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("[t24-hold-ctrl] Bad release request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[t24-hold-ctrl] Internal error releasing hold", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @Operation(summary = "Get hold by reference", description = "Retrieves current status and details of an active or released hold.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hold found"),
            @ApiResponse(responseCode = "404", description = "Hold reference not found")
    })
    @GetMapping(value = "/{referenceNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getHold(
            @Parameter(description = "Remittance or transaction reference number", example = "REM-202610-001")
            @PathVariable String referenceNo) {
        return holdService.getHold(referenceNo)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
