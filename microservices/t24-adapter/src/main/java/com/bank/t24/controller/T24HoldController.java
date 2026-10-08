package com.bank.t24.controller;

import com.bank.t24.dto.T24HoldRequest;
import com.bank.t24.dto.T24HoldResponse;
import com.bank.t24.dto.T24ReleaseRequest;
import com.bank.t24.service.T24HoldService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/t24/holds")
public class T24HoldController {

    private static final Logger log = LoggerFactory.getLogger(T24HoldController.class);

    private final T24HoldService holdService;

    public T24HoldController(T24HoldService holdService) {
        this.holdService = holdService;
    }

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

    @GetMapping(value = "/{referenceNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getHold(@PathVariable String referenceNo) {
        return holdService.getHold(referenceNo)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
