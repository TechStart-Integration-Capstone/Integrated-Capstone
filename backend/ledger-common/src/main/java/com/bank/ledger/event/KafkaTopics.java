package com.bank.ledger.event;

/**
 * Kafka topic and consumer-group names shared by every service (single source of truth).
 */
public final class KafkaTopics {

    /** Main event stream. Key = accountId, so all events of one account stay in order on one partition. */
    public static final String TRANSACTION_EVENTS = "transaction-events";

    /** Dead-letter topic: events that failed all retries or are malformed. */
    public static final String TRANSACTION_EVENTS_DLT = "transaction-events.DLT";

    public static final int PARTITIONS = 6;

    public static final String AUDIT_GROUP = "ledger-audit-group";
    public static final String RECON_GROUP = "ledger-recon-group";
    public static final String NOTIFICATION_GROUP = "ledger-notification-group";

    private KafkaTopics() {}
}
