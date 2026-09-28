package com.bank.audit.repository;

import com.bank.audit.model.LedgerMutationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LedgerMutationAuditRepository extends JpaRepository<LedgerMutationAudit, Long> {
    Optional<LedgerMutationAudit> findByTransactionId(Long transactionId);
}
