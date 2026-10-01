package com.bank.ledger.repository.postgres;

import com.bank.ledger.model.postgres.LedgerMutationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerMutationAuditRepository extends JpaRepository<LedgerMutationAudit, Long> {
    Optional<LedgerMutationAudit> findByTransactionIdAndAccountId(Long transactionId, Long accountId);
    List<LedgerMutationAudit> findByTransactionId(Long transactionId);
    List<LedgerMutationAudit> findByTransactionIdIn(Collection<Long> transactionIds);
    boolean existsByTransactionIdAndAccountId(Long transactionId, Long accountId);
    List<LedgerMutationAudit> findByAccountIdOrderByCreatedDateDesc(Long accountId);
    List<LedgerMutationAudit> findTop50ByOrderByCreatedDateDesc();
}

