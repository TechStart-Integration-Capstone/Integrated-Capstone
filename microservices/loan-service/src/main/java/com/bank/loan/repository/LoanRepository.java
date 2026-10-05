package com.bank.loan.repository;

import com.bank.loan.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByCustomerIdOrderByLoanIdDesc(Long customerId);

    boolean existsByCustomerIdAndStatus(Long customerId, String status);

    /** Pessimistic row lock for repayment and EOD — always taken before any LOAN_SCHEDULE row. */
    @Query(value = "SELECT * FROM dbo.LOAN WITH (UPDLOCK, ROWLOCK) WHERE loan_id = :loanId", nativeQuery = true)
    Optional<Loan> lockById(@Param("loanId") Long loanId);
}
