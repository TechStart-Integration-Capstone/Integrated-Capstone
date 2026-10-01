package com.bank.ledger.repository.postgres;

import com.bank.ledger.model.postgres.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {
    Optional<ReconciliationLog> findByTransactionIdAndAccountId(Long transactionId, Long accountId);
    Optional<ReconciliationLog> findTopByTransactionIdOrderByReconDateDesc(Long transactionId);
    List<ReconciliationLog> findByTransactionId(Long transactionId);
    List<ReconciliationLog> findByReconStatusOrderByReconDateDesc(String reconStatus);
    List<ReconciliationLog> findTop50ByOrderByReconDateDesc();
    long countByReconStatus(String reconStatus);
}

