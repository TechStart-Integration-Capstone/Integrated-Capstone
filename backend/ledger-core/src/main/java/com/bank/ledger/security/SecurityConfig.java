package com.bank.ledger.security;

import com.bank.ledger.dto.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        // Public Auth & Health endpoints
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/", "/index.html", "/src/**", "/static/**", "/css/**", "/js/**", "/images/**").permitAll()

                        // Role-Based Access Control
                        .requestMatchers("/api/v1/ledger/mutate/**").hasAnyRole("CUSTOMER", "RETAIL_USER", "TELLER", "ADMIN")
                        .requestMatchers("/api/v1/accounts/**").authenticated()
                        .requestMatchers("/api/v1/reconciliation/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/telemetry/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/stress/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Strict default: deny all unmatched
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            writeRfc7807(response, HttpStatus.UNAUTHORIZED,
                    "https://api.paypink.ph/errors/unauthorized",
                    "Authentication Required",
                    "Access denied. A valid JWT Bearer token is required to access this financial endpoint.",
                    request.getRequestURI());
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            writeRfc7807(response, HttpStatus.FORBIDDEN,
                    "https://api.paypink.ph/errors/forbidden",
                    "Access Forbidden",
                    "Insufficient privileges. Your current role does not have authorization to perform this operation.",
                    request.getRequestURI());
        };
    }

    private void writeRfc7807(HttpServletResponse response, HttpStatus status, String type, String title, String detail, String instance) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetails problem = new ProblemDetails(type, title, status.value(), detail, instance);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
        response.getWriter().flush();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization", "Idempotency-Key", "Idempotent-Replay", "X-Response-Time"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
