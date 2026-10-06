package com.bank.loan.repository;

import com.bank.loan.model.LoanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LoanScheduleRepository extends JpaRepository<LoanSchedule, Long> {

    List<LoanSchedule> findByLoanIdOrderByInstallmentNo(Long loanId);

    /** Loans with at least one PENDING installment due before the business date (EOD candidates). */
    @Query("SELECT DISTINCT s.loanId FROM LoanSchedule s WHERE s.status = 'PENDING' AND s.dueDate < :businessDate")
    List<Long> findLoanIdsWithPendingDueBefore(@Param("businessDate") LocalDate businessDate);
}
