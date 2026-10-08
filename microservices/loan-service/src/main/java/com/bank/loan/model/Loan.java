package com.bank.loan.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "LOAN", schema = "t24")
public class Loan {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_CLOSED = "CLOSED";

    /** Outcome of the last EOD auto-debit (last_autodebit_status). */
    public static final String AUTODEBIT_PAID = "PAID";
    public static final String AUTODEBIT_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String AUTODEBIT_FAILED = "FAILED"; // core banking unavailable; retried next EOD

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "reference_no", nullable = false, unique = true, length = 30)
    private String referenceNo;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "principal", nullable = false, precision = 18, scale = 4)
    private BigDecimal principal;

    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal annualRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "monthly_installment", nullable = false, precision = 18, scale = 4)
    private BigDecimal monthlyInstallment;

    @Column(name = "outstanding_principal", nullable = false, precision = 18, scale = 4)
    private BigDecimal outstandingPrincipal;

    @Column(name = "penalty_due", nullable = false, precision = 18, scale = 4)
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_ACTIVE; // ACTIVE | OVERDUE | CLOSED

    @Column(name = "disbursement_txn_id")
    private Long disbursementTxnId;

    @Column(name = "ft_reference", length = 20)
    private String ftReference;

    @Column(name = "disbursed_date", nullable = false)
    private LocalDate disbursedDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "last_autodebit_date")
    private LocalDate lastAutoDebitDate;

    @Column(name = "last_autodebit_status", length = 20)
    private String lastAutoDebitStatus;

    @Column(name = "last_autodebit_amount", precision = 18, scale = 4)
    private BigDecimal lastAutoDebitAmount;

    public Loan() {}

    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }
    public BigDecimal getAnnualRate() { return annualRate; }
    public void setAnnualRate(BigDecimal annualRate) { this.annualRate = annualRate; }
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }
    public BigDecimal getMonthlyInstallment() { return monthlyInstallment; }
    public void setMonthlyInstallment(BigDecimal monthlyInstallment) { this.monthlyInstallment = monthlyInstallment; }
    public BigDecimal getOutstandingPrincipal() { return outstandingPrincipal; }
    public void setOutstandingPrincipal(BigDecimal outstandingPrincipal) { this.outstandingPrincipal = outstandingPrincipal; }
    public BigDecimal getPenaltyDue() { return penaltyDue; }
    public void setPenaltyDue(BigDecimal penaltyDue) { this.penaltyDue = penaltyDue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDisbursementTxnId() { return disbursementTxnId; }
    public void setDisbursementTxnId(Long disbursementTxnId) { this.disbursementTxnId = disbursementTxnId; }
    public String getFtReference() { return ftReference; }
    public void setFtReference(String ftReference) { this.ftReference = ftReference; }
    public LocalDate getDisbursedDate() { return disbursedDate; }
    public void setDisbursedDate(LocalDate disbursedDate) { this.disbursedDate = disbursedDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public LocalDate getLastAutoDebitDate() { return lastAutoDebitDate; }
    public void setLastAutoDebitDate(LocalDate lastAutoDebitDate) { this.lastAutoDebitDate = lastAutoDebitDate; }
    public String getLastAutoDebitStatus() { return lastAutoDebitStatus; }
    public void setLastAutoDebitStatus(String lastAutoDebitStatus) { this.lastAutoDebitStatus = lastAutoDebitStatus; }
    public BigDecimal getLastAutoDebitAmount() { return lastAutoDebitAmount; }
    public void setLastAutoDebitAmount(BigDecimal lastAutoDebitAmount) { this.lastAutoDebitAmount = lastAutoDebitAmount; }
}
