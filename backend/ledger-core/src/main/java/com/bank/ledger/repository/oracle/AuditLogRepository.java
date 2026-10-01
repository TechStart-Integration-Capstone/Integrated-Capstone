package com.bank.ledger.repository.oracle;

import com.bank.ledger.model.oracle.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByCustomerIdOrderByTimestampDesc(Long customerId);
    List<AuditLog> findTop50ByOrderByTimestampDesc();
}
