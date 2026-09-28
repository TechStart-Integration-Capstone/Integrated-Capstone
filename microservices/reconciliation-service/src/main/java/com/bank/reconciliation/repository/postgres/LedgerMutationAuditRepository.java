package com.bank.reconciliation.repository.postgres;

import com.bank.reconciliation.model.postgres.LedgerMutationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LedgerMutationAuditRepository extends JpaRepository<LedgerMutationAudit, Long> {
    Optional<LedgerMutationAudit> findByTransactionId(Long transactionId);
}
