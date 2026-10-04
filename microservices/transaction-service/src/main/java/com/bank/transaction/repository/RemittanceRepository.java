package com.bank.transaction.repository;

import com.bank.transaction.model.Remittance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RemittanceRepository extends JpaRepository<Remittance, Long> {
    Optional<Remittance> findByReferenceNo(String referenceNo);
    java.util.List<Remittance> findByStatus(String status);
}
