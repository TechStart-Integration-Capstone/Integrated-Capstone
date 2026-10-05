package com.bank.loan.repository;

import com.bank.loan.model.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    Optional<LoanRepayment> findByIdempotencyKey(String idempotencyKey);
}
