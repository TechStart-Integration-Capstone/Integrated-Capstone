package com.bank.audit.repository;

import com.bank.audit.model.RiskDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskDecisionRepository extends JpaRepository<RiskDecision, Long> {

    /** Used for idempotency — skip if already recorded for this reference. */
    Optional<RiskDecision> findByReferenceNo(String referenceNo);

    /** Query endpoint: filter by decision (APPROVE / REJECT / UNAVAILABLE). */
    Page<RiskDecision> findByDecisionOrderByScoredAtDesc(String decision, Pageable pageable);

    /** Query endpoint: filter by time range, newest first. */
    Page<RiskDecision> findByScoredAtBetweenOrderByScoredAtDesc(
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    /** Query endpoint: all decisions, newest first (paged). */
    Page<RiskDecision> findAllByOrderByScoredAtDesc(Pageable pageable);

    /** Dashboard stat: count decisions grouped by decision value since a given time. */
    @Query("SELECT r.decision, COUNT(r) FROM RiskDecision r WHERE r.scoredAt >= :since GROUP BY r.decision")
    List<Object[]> countByDecisionSince(@Param("since") OffsetDateTime since);
}
