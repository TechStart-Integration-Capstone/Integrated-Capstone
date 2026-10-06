package com.bank.loan.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "LOAN_SCHEDULE")
public class LoanSchedule {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_OVERDUE = "OVERDUE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "installment_no", nullable = false)
    private Integer installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 18, scale = 4)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 18, scale = 4)
    private BigDecimal interestDue;

    @Column(name = "amount_paid", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "penalty_charged", nullable = false)
    private boolean penaltyCharged;

    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_PENDING; // PENDING | PAID | OVERDUE

    public LoanSchedule() {}

    public LoanSchedule(Long loanId, int installmentNo, LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue) {
        this.loanId = loanId;
        this.installmentNo = installmentNo;
        this.dueDate = dueDate;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
    }

    /** Principal + interest for this row. */
    public BigDecimal totalDue() { return principalDue.add(interestDue); }

    /** What is still owed on this row. */
    public BigDecimal remaining() { return totalDue().subtract(amountPaid); }

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public Integer getInstallmentNo() { return installmentNo; }
    public void setInstallmentNo(Integer installmentNo) { this.installmentNo = installmentNo; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getPrincipalDue() { return principalDue; }
    public void setPrincipalDue(BigDecimal principalDue) { this.principalDue = principalDue; }
    public BigDecimal getInterestDue() { return interestDue; }
    public void setInterestDue(BigDecimal interestDue) { this.interestDue = interestDue; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
    public boolean isPenaltyCharged() { return penaltyCharged; }
    public void setPenaltyCharged(boolean penaltyCharged) { this.penaltyCharged = penaltyCharged; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
