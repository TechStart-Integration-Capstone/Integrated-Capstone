package com.bank.reconciliation.repository.postgres;

import com.bank.reconciliation.model.postgres.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

@Repository
public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {
    List<ReconciliationLog> findTop50ByOrderByReconDateDesc();
    Optional<ReconciliationLog> findByTransactionId(Long transactionId);
}
