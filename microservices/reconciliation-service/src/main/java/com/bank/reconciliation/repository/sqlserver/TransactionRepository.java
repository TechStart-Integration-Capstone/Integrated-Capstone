package com.bank.reconciliation.repository.sqlserver;

import com.bank.reconciliation.model.sqlserver.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    List<TransactionRecord> findTop50ByOrderByTransactionDateDesc();
}
