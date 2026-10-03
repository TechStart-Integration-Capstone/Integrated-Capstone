package com.bank.outbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox Publisher Service
 *
 * Dedicated microservice responsible for the CDC / polling half of the
 * Transactional Outbox Pattern.
 *
 * Responsibility:
 *   - Poll the Azure SQL OUTBOX_EVENT table for rows with status = 'PENDING'
 *   - Publish each event payload to the Kafka topic ledger.transaction.events
 *   - Mark successfully published events as 'PROCESSED'; failures as 'FAILED'
 *     with a retry counter so they are retried on the next poll cycle
 *
 * This service is intentionally kept thin: no HTTP mutation endpoints,
 * no Redis. It only reads Azure SQL and writes to Kafka.
 *
 * Architecture layer: Event Layer (Layer 5) — sits between Azure SQL and
 * the Kafka consumer services (audit, notification, reconciliation, analytics).
 */
@SpringBootApplication
@EnableScheduling
public class OutboxPublisherApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutboxPublisherApplication.class, args);
    }
}
