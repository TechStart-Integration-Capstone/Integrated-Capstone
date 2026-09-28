package com.bank.outbox.repository;

import com.bank.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for polling and updating OUTBOX_EVENT rows in Oracle XE.
 *
 * The custom query uses FETCH FIRST N ROWS ONLY (Oracle syntax) to cap
 * each poll batch — prevents a thundering-herd if a large backlog builds up
 * after a Kafka outage. Batch size is configurable via app.outbox.batch-size.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Returns up to {@code batchSize} PENDING events ordered by creation date
     * (oldest-first) to preserve approximate event ordering on the Kafka topic.
     *
     * Native query used because JPQL does not support FETCH FIRST N ROWS ONLY.
     */
    @Query(value = """
            SELECT * FROM OUTBOX_EVENT
             WHERE status = 'PENDING'
             ORDER BY created_date ASC
             FETCH FIRST :batchSize ROWS ONLY
            """,
            nativeQuery = true)
    List<OutboxEvent> findPendingBatch(int batchSize);

    /**
     * Fallback query — finds FAILED events that are eligible for retry.
     * Called on a slower schedule (every 30 s) to back-fill anything that
     * failed on the first attempt without flooding Kafka on normal operation.
     */
    @Query(value = """
            SELECT * FROM OUTBOX_EVENT
             WHERE status = 'FAILED'
             ORDER BY created_date ASC
             FETCH FIRST :batchSize ROWS ONLY
            """,
            nativeQuery = true)
    List<OutboxEvent> findFailedBatch(int batchSize);
}
