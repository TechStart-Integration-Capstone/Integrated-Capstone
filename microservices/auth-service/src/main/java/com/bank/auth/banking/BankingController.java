package com.bank.auth.banking;

import com.bank.auth.dto.AuthRequest;
import com.bank.auth.dto.AuthResponse;
import com.bank.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth/banking")
public class BankingController {
    private final BankingService banking;
    private final AuthService auth;
    private final BankingTransferService transfers;
    private final BankingRecipientService recipients;
    public BankingController(BankingService banking, AuthService auth, BankingTransferService transfers, BankingRecipientService recipients) {
        this.banking = banking; this.auth = auth; this.transfers = transfers;
        this.recipients = recipients;
    }

    @GetMapping("/recipients/lookup")
    public BankingRecipientService.Recipient lookup(@RequestHeader(value="Authorization",required=false) String token, @RequestParam String accountNumber) {
        return recipients.lookup(token,accountNumber);
    }
    @GetMapping("/recipients")
    public BankingRecipientService.Directory recipients(@RequestHeader(value="Authorization",required=false) String token) {
        return recipients.directory(token);
    }
    public record FavoriteRequest(@jakarta.validation.constraints.NotBlank String accountNumber) {}
    @PostMapping("/favorites")
    public BankingRecipientService.Recipient favorite(@RequestHeader(value="Authorization",required=false) String token, @Valid @RequestBody FavoriteRequest request) {
        return recipients.save(token,request.accountNumber());
    }
    @DeleteMapping("/favorites/{number}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@RequestHeader(value="Authorization",required=false) String token, @PathVariable String number) {
        recipients.remove(token,number);
    }

    @PostMapping("/transfers")
    public BankingTransferService.Receipt transfer(@RequestHeader(value = "Authorization", required = false) String token,
                                                   @Valid @RequestBody BankingTransferService.Request request) {
        try { return transfers.transfer(token, request); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request conflicts with another transfer. Check your history before starting a new one.");
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody BankingService.Registration request) {
        return banking.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = auth.authenticate(request);
            banking.authenticatedCustomer("Bearer " + response.getToken());
            return response;
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Banking is temporarily unavailable. Try again shortly.");
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your username or password is incorrect.");
        }
    }

    @GetMapping("/me")
    public BankingService.Profile profile(@RequestHeader(value = "Authorization", required = false) String token) {
        return banking.profile(token);
    }

    @GetMapping("/transactions")
    public List<BankingService.Activity> transactions(@RequestHeader(value = "Authorization", required = false) String token) {
        return banking.activity(token);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", Objects.requireNonNullElse(ex.getReason(), "Request failed.")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        if (ex.getBindingResult().getTarget() instanceof BankingTransferService.Request)
            return ResponseEntity.badRequest().body(Map.of("message", "Choose a source account, a valid recipient account number, and an amount of at least PHP 0.01 with no more than two decimal places."));
        return ResponseEntity.badRequest().body(Map.of("message", "Please check your details. Use a valid email, phone number, and a password of 8–64 characters."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "That username or email is already registered."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "Banking is temporarily unavailable. Try again shortly."));
    }
}
