package com.bank.auth.banking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "External Transfers", description = "Simulated external partner rails (InstaPay / PESONet)")
@RestController
@RequestMapping("/api/v1/auth/banking/external")
public class ExternalTransferController {
    private final ExternalTransferService transfers;
    private final BankingService banking;
    public ExternalTransferController(ExternalTransferService transfers,BankingService banking) { this.transfers=transfers; this.banking=banking; }

    @Operation(summary = "List external transfer recipients", description = "Retrieves simulated partner bank recipients directory.")
    @GetMapping("/recipients")
    public List<ExternalTransferService.Recipient> recipients(@Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        banking.authenticatedCustomer(token); return ExternalTransferService.RECIPIENTS;
    }

    @Operation(summary = "External transfer history", description = "Retrieves transaction history for external rails.")
    @GetMapping("/transfers")
    public List<ExternalTransferService.Receipt> history(@Parameter(hidden = true) @RequestHeader("Authorization") String token) { return transfers.history(token); }

    @Operation(summary = "Execute external transfer", description = "Simulates outward credit transfer via external clearing rail.")
    @PostMapping("/transfers")
    public ExternalTransferService.Receipt transfer(@Parameter(hidden = true) @RequestHeader("Authorization") String token, @Valid @RequestBody ExternalTransferService.Request request) {
        return transfers.transfer(token,request);
    }
}
