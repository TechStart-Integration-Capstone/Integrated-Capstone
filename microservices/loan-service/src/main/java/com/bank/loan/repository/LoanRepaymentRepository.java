package com.bank.loan.repository;

import com.bank.loan.model.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    Optional<LoanRepayment> findByIdempotencyKey(String idempotencyKey);
    boolean existsByLoanIdAndStatus(Long loanId, String status);
    List<LoanRepayment> findByStatusOrderByCreatedDateAsc(String status);
}
