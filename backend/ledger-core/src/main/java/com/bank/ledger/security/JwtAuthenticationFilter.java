package com.bank.ledger.security;

import com.bank.ledger.service.AuthService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final AuthService authService;
    private final Timer tokenCheckTimer;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   @Lazy AuthService authService,
                                   @Autowired(required = false) MeterRegistry meterRegistry) {
        this.tokenProvider = tokenProvider;
        this.authService = authService;
        this.tokenCheckTimer = meterRegistry != null
                ? Timer.builder("ledger.token.check")
                       .description("Latency for Redis token matrix active status verification")
                       .register(meterRegistry)
                : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);

        if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
            String jti = tokenProvider.getJtiFromToken(jwt);
            
            long startNanos = System.nanoTime();
            boolean isActive = true;
            if (jti != null && authService != null) {
                isActive = authService.isTokenActive(jti);
            }
            if (tokenCheckTimer != null) {
                tokenCheckTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            }

            if (isActive) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                List<String> roles = tokenProvider.getRolesFromToken(jwt);

                List<SimpleGrantedAuthority> authorities = roles != null
                        ? roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())
                        : List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
