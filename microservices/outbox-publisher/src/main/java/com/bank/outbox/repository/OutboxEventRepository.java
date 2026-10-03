package com.bank.outbox.repository;

import com.bank.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for polling and updating OUTBOX_EVENT rows in Azure SQL Database.
 *
 * SQL Server OFFSET/FETCH caps each poll batch and prevents a thundering herd
 * if a large backlog builds up
 * after a Kafka outage. Batch size is configurable via app.outbox.batch-size.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Returns up to {@code batchSize} PENDING events ordered by creation date
     * (oldest-first) to preserve approximate event ordering on the Kafka topic.
     *
     * SQL Server's OFFSET/FETCH syntax requires an ORDER BY clause.
     */
    @Query(value = """
            SELECT * FROM OUTBOX_EVENT
             WHERE status = 'PENDING'
             ORDER BY created_date ASC
             OFFSET 0 ROWS FETCH NEXT :batchSize ROWS ONLY
            """,
            nativeQuery = true)
    List<OutboxEvent> findPendingBatch(@Param("batchSize") int batchSize);

    /**
     * Fallback query — finds FAILED events that are eligible for retry.
     * Called on a slower schedule (every 30 s) to back-fill anything that
     * failed on the first attempt without flooding Kafka on normal operation.
     */
    @Query(value = """
            SELECT * FROM OUTBOX_EVENT
             WHERE status = 'FAILED'
             ORDER BY created_date ASC
             OFFSET 0 ROWS FETCH NEXT :batchSize ROWS ONLY
            """,
            nativeQuery = true)
    List<OutboxEvent> findFailedBatch(@Param("batchSize") int batchSize);
}
