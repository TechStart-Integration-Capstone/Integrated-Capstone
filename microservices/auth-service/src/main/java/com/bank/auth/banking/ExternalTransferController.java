package com.bank.auth.banking;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/banking/external")
public class ExternalTransferController {
    private final ExternalTransferService transfers;
    private final BankingService banking;
    public ExternalTransferController(ExternalTransferService transfers,BankingService banking) { this.transfers=transfers; this.banking=banking; }
    @GetMapping("/recipients")
    public List<ExternalTransferService.Recipient> recipients(@RequestHeader("Authorization") String token) {
        banking.authenticatedCustomer(token); return ExternalTransferService.RECIPIENTS;
    }
    @GetMapping("/transfers")
    public List<ExternalTransferService.Receipt> history(@RequestHeader("Authorization") String token) { return transfers.history(token); }
    @PostMapping("/transfers")
    public ExternalTransferService.Receipt transfer(@RequestHeader("Authorization") String token,@Valid @RequestBody ExternalTransferService.Request request) {
        return transfers.transfer(token,request);
    }
}
