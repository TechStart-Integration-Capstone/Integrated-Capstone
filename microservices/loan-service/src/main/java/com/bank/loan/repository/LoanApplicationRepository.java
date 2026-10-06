package com.bank.loan.repository;

import com.bank.loan.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findByIdempotencyKey(String idempotencyKey);

    /** Pessimistic row lock for accept/disburse â€” T-SQL hint in the FROM clause. */
    @Query(value = "SELECT * FROM dbo.LOAN_APPLICATION WITH (UPDLOCK, ROWLOCK) WHERE reference_no = :referenceNo",
           nativeQuery = true)
    Optional<LoanApplication> lockByReferenceNo(@Param("referenceNo") String referenceNo);

    /**
     * Disbursements to finish: DISBURSING rows, plus DECIDED rows whose LOAN-DISB transfer already exists in
     * transaction-service (left behind by the earlier single-transaction accept, which rolled back after the money moved).
     */
    @Query(value = "SELECT a.* FROM dbo.LOAN_APPLICATION a WHERE a.status = 'DISBURSING' "
            + "OR (a.status = 'DECIDED' AND EXISTS (SELECT 1 FROM dbo.REMITTANCE r "
            + "WHERE r.idempotency_key = CONCAT('LOAN-DISB-', a.reference_no) "
            + "AND r.status NOT IN ('Failed', 'Rejected', 'Cancelled')))",
           nativeQuery = true)
    List<LoanApplication> findDisbursementsToRecover();

    /** Offers already being disbursed for the customer (money out, loan not recorded yet) — counts toward the credit limit. */
    @Query("SELECT COALESCE(SUM(a.offeredAmount), 0) FROM LoanApplication a "
            + "WHERE a.customerId = :customerId AND a.status = 'DISBURSING' AND a.applicationId <> :excludeId")
    BigDecimal sumDisbursingAmount(@Param("customerId") Long customerId, @Param("excludeId") Long excludeId);
}
