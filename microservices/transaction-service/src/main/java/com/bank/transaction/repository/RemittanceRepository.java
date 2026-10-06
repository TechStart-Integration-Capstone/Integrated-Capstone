package com.bank.transaction.repository;

import com.bank.transaction.model.Remittance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RemittanceRepository extends JpaRepository<Remittance, Long> {
    Optional<Remittance> findByReferenceNo(String referenceNo);
    Optional<Remittance> findByCallerCustomerIdAndIdempotencyKey(Long callerCustomerId, String idempotencyKey);
    java.util.List<Remittance> findByStatus(String status);
    java.util.List<Remittance> findByStatusIn(java.util.Collection<String> statuses);
    java.util.List<Remittance> findByStatusAndInternalStatusAndCancelUntilBefore(String status, String internalStatus, java.time.LocalDateTime time);
    java.util.List<Remittance> findByStatusAndNextRetryAtBefore(String status, java.time.LocalDateTime time);
    Optional<Remittance> findByReferenceNoAndCallerCustomerId(String referenceNo, Long callerCustomerId);
}
