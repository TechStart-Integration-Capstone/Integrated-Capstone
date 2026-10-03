package com.bank.ledger.repository.sqlserver;

import com.bank.ledger.model.sqlserver.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByCustomerIdOrderByTimestampDesc(Long customerId);
    List<AuditLog> findTop50ByOrderByTimestampDesc();
}
