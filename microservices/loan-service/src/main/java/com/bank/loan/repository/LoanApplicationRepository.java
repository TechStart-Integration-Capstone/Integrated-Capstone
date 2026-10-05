package com.bank.loan.repository;

import com.bank.loan.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findByIdempotencyKey(String idempotencyKey);

    /** Pessimistic row lock for accept/disburse — T-SQL hint in the FROM clause. */
    @Query(value = "SELECT * FROM dbo.LOAN_APPLICATION WITH (UPDLOCK, ROWLOCK) WHERE reference_no = :referenceNo",
           nativeQuery = true)
    Optional<LoanApplication> lockByReferenceNo(@Param("referenceNo") String referenceNo);
}
