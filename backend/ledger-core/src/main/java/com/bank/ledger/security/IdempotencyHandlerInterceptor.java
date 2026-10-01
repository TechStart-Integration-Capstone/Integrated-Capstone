package com.bank.ledger.security;

import com.bank.ledger.dto.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Idempotency gate for POST /api/v1/ledger/mutate.
 *
 * preHandle:       lookup + atomic reservation (SET NX EX) in Redis.
 *                  - cached response  -> 200 replay (Idempotent-Replay: true)
 *                  - IN_PROGRESS      -> 409 Conflict
 *                  - absent           -> reserve, continue
 * afterCompletion: on any error response, release the reservation (only if still IN_PROGRESS)
 *                  so the client can retry immediately.
 *
 * The final response is written to Redis by LedgerMutationService in afterCommit().
 */
@Component
public class IdempotencyHandlerInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Idempotency-Key";
    public static final String LEGACY_HEADER = "X-Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyHandlerInterceptor(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Single source of truth for which headers carry the idempotency key.
     * Used by this interceptor AND LedgerMutationController so both always agree.
     */
    public static String resolveKey(HttpServletRequest request) {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            key = request.getHeader(LEGACY_HEADER);
        }
        return (key == null || key.isBlank()) ? null : key.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/ledger/mutate")) {
            return true;
        }

        String idempotencyKey = resolveKey(request);
        if (idempotencyKey == null) {
            writeRfc7807Error(response, HttpStatus.BAD_REQUEST,
                    "https://api.paypink.ph/errors/missing-idempotency-key",
                    "Missing Idempotency-Key",
                    "A unique 'Idempotency-Key' HTTP header is mandatory for all balance mutation requests.",
                    path);
            return false;
        }

        String state = idempotencyService.get(idempotencyKey);
        if (state != null) {
            if (IdempotencyService.IN_PROGRESS.equals(state)) {
                writeConflict(response, idempotencyKey, path);
            } else {
                // Cached completed JSON response
                response.setStatus(HttpStatus.OK.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Idempotent-Replay", "true");
                response.getWriter().write(state);
                response.getWriter().flush();
            }
            // preHandle returned false -> afterCompletion is NOT called -> the key is never released here
            return false;
        }

        // Key is absent: attempt atomic reservation
        if (!idempotencyService.reserve(idempotencyKey)) {
            writeConflict(response, idempotencyKey, path);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Success: afterCommit() already stored the response; keep it
        if (ex == null && response.getStatus() < 400) {
            return;
        }
        String key = resolveKey(request);
        if (key != null) {
            idempotencyService.releaseIfInProgress(key);
        }
    }

    private void writeConflict(HttpServletResponse response, String key, String path) throws IOException {
        writeRfc7807Error(response, HttpStatus.CONFLICT,
                "https://api.paypink.ph/errors/concurrent-mutation-in-progress",
                "Concurrent Mutation In Progress",
                "A mutation request with Idempotency-Key '" + key + "' is currently being processed by the core engine.",
                path);
    }

    private void writeRfc7807Error(HttpServletResponse response, HttpStatus status, String type,
                                   String title, String detail, String instance) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        ProblemDetails problem = new ProblemDetails(type, title, status.value(), detail, instance);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
        response.getWriter().flush();
    }
}
