package com.bank.reconciliation.repository.postgres;

import com.bank.reconciliation.model.postgres.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {
    List<ReconciliationLog> findTop50ByOrderByReconDateDesc();
}
