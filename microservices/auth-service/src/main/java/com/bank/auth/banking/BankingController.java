package com.bank.auth.banking;

import com.bank.auth.dto.AuthRequest;
import com.bank.auth.dto.AuthResponse;
import com.bank.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Tag(name = "Banking Customer & Onboarding", description = "Customer onboarding, authentication, profile lookup, and directory endpoints")
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

    /**
     * @deprecated Deprecated in PayPink 2.0 (Phase 5). API Gateway rewrites /api/v1/auth/banking/recipients
     * to account-service (/api/v1/accounts/recipients). Kept for backward compatibility.
     */
    @Deprecated
    @Operation(summary = "Lookup recipient by account number (Deprecated)", description = "Deprecated: Gateway routes to account-service. Resolves recipient name by account number.")
    @GetMapping("/recipients/lookup")
    public BankingRecipientService.Recipient lookup(@Parameter(hidden = true) @RequestHeader(value="Authorization",required=false) String token, @Parameter(description = "Account number to lookup", example = "ACC-1002") @RequestParam String accountNumber) {
        return recipients.lookup(token,accountNumber);
    }

    /**
     * @deprecated Deprecated in PayPink 2.0 (Phase 5). API Gateway rewrites /api/v1/auth/banking/recipients
     * to account-service (/api/v1/accounts/recipients). Kept for backward compatibility.
     */
    @Deprecated
    @Operation(summary = "Get recipient directory (Deprecated)", description = "Deprecated: Gateway routes to account-service. Returns directory of available transfer recipients.")
    @GetMapping("/recipients")
    public BankingRecipientService.Directory recipients(@Parameter(hidden = true) @RequestHeader(value="Authorization",required=false) String token) {
        return recipients.directory(token);
    }

    public record FavoriteRequest(@jakarta.validation.constraints.NotBlank String accountNumber) {}

    /**
     * @deprecated Deprecated in PayPink 2.0 (Phase 5). API Gateway rewrites /api/v1/auth/banking/favorites
     * to account-service (/api/v1/accounts/favorites). Kept for backward compatibility.
     */
    @Deprecated
    @Operation(summary = "Save recipient to favorites (Deprecated)", description = "Deprecated: Gateway routes to account-service. Saves an account to customer favorites.")
    @PostMapping("/favorites")
    public BankingRecipientService.Recipient favorite(@Parameter(hidden = true) @RequestHeader(value="Authorization",required=false) String token, @Valid @RequestBody FavoriteRequest request) {
        return recipients.save(token,request.accountNumber());
    }

    /**
     * @deprecated Deprecated in PayPink 2.0 (Phase 5). API Gateway rewrites /api/v1/auth/banking/favorites
     * to account-service (/api/v1/accounts/favorites). Kept for backward compatibility.
     */
    @Deprecated
    @Operation(summary = "Remove recipient from favorites (Deprecated)", description = "Deprecated: Gateway routes to account-service. Removes an account from favorites.")
    @DeleteMapping("/favorites/{number}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@Parameter(hidden = true) @RequestHeader(value="Authorization",required=false) String token, @Parameter(description = "Account number to remove") @PathVariable String number) {
        recipients.remove(token,number);
    }

    @Deprecated
    @Operation(summary = "Direct transfer (Gone/Deprecated)", description = "Deprecated (410 Gone). All transfers must be routed via /api/v1/remittance/transfer.")
    @PostMapping("/transfers")
    public BankingTransferService.Receipt transfer(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String token,
                                                   @RequestBody(required = false) Object request) {
        throw new ResponseStatusException(HttpStatus.GONE,
                "Direct database transfer is deprecated. All transfers must be routed via /api/v1/remittance/transfer.");
    }

    @Operation(summary = "Register new banking customer", description = "Registers a new customer account, creating credentials and initial deposit account.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration successful"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Username or email already registered")
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody BankingService.Registration request) {
        return banking.register(request);
    }

    @Operation(summary = "Banking login", description = "Authenticates banking user credentials and verifies customer profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
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

    @Operation(summary = "Get banking profile", description = "Retrieves the authenticated customer's profile, linked accounts, and status.")
    @GetMapping("/me")
    public BankingService.Profile profile(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String token) {
        return banking.profile(token);
    }

    @Operation(summary = "Get banking activity", description = "Retrieves recent banking transactions for the authenticated customer.")
    @GetMapping("/transactions")
    public List<BankingService.Activity> transactions(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String token) {
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
