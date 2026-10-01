package com.bank.ledger.repository.oracle;

import com.bank.ledger.model.oracle.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP LOCKED
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' AND e.nextAttemptAt <= :now ORDER BY e.createdDate")
    List<OutboxEvent> claimBatch(@Param("now") LocalDateTime now, Pageable page);

    List<OutboxEvent> findByStatusOrderByCreatedDateAsc(String status);
    List<OutboxEvent> findTop100ByOrderByCreatedDateDesc();
    long countByStatus(String status);
}

