package com.bank.loan.repository;

import com.bank.loan.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByCustomerIdOrderByLoanIdDesc(Long customerId);

    boolean existsByCustomerIdAndStatus(Long customerId, String status);

    Optional<Loan> findByApplicationId(Long applicationId);

    /** Principal still owed on the customer's open (ACTIVE or OVERDUE) loans. */
    @Query("SELECT COALESCE(SUM(l.outstandingPrincipal), 0) FROM Loan l WHERE l.customerId = :customerId AND l.status <> 'CLOSED'")
    BigDecimal sumOpenPrincipal(@Param("customerId") Long customerId);

    /** Pessimistic row lock for repayment and EOD — always taken before any LOAN_SCHEDULE row. */
    @Query(value = "SELECT * FROM dbo.LOAN WITH (UPDLOCK, ROWLOCK) WHERE loan_id = :loanId", nativeQuery = true)
    Optional<Loan> lockById(@Param("loanId") Long loanId);
}
