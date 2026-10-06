package com.bank.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class AuditKafkaConfiguration {
    @Bean
    DefaultErrorHandler auditErrorHandler() {
        // Keep a failed financial event pending until the audit database accepts it.
        return new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
