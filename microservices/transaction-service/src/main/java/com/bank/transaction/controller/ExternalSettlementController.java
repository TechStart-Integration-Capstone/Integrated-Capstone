package com.bank.transaction.controller;

import com.bank.transaction.service.ExternalSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Like loan settlement, this endpoint is private and has no gateway route. */
@RestController
@RequestMapping("/internal/remittance")
public class ExternalSettlementController {
    public record Request(@NotBlank @Pattern(regexp="EXT-[A-Za-z0-9_-]{43}") String reference,
                          @NotNull @Positive Long customerId) {}
    private final ExternalSettlementService settlement;
    public ExternalSettlementController(ExternalSettlementService settlement) { this.settlement = settlement; }

    @PostMapping("/external")
    public Map<String,String> settle(@RequestHeader(value="X-Internal-Service",required=false) String caller,
                                     @Valid @RequestBody Request request) {
        if (!"auth-service".equals(caller))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal endpoint: caller not allowed");
        return Map.of("status", settlement.settle(request.reference(), request.customerId()));
    }
}
