package com.bank.transaction.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT perimeter for the transaction-service.
 *
 * JWT validation is handled upstream at the API Gateway (JwtAuthFilter).
 * Downstream services receive pre-validated headers (X-Auth-Username,
 * X-Auth-Customer-Id) and trust them — no redundant token parsing here.
 *
 * Exposed paths:
 *   POST /api/v1/ledger/mutate          — ledger balance mutation
 *   POST /api/v1/stress/double-spend-test — race condition stress test (Tab 2)
 *   GET  /api/v1/telemetry/stats         — live performance stats (Tab 6)
 *   GET  /actuator/**                    — Spring Boot health / prometheus
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/ledger/**",
                    "/api/v1/stress/**",
                    "/api/v1/telemetry/**",
                    "/actuator/**"
                ).permitAll()
                .anyRequest().permitAll()   // catch-all — gateway is the real perimeter
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Idempotency-Key"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
