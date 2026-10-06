package com.bank.loan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loan rules from the `loan:` block of application.yml. */
@ConfigurationProperties(prefix = "loan")
public class LoanProperties {

    private BigDecimal minAmount = new BigDecimal("5000");
    private int minTerm = 3;
    private int maxTerm = 60;
    private BigDecimal affordabilityRatio = new BigDecimal("0.30");
    private int declineBelowScore = 500;
    private int offerValidDays = 7;
    private BigDecimal penaltyRate = new BigDecimal("0.02");
    private String bankAccountNo = "PH1000000LOAN";
    private Map<String, Band> bands = new LinkedHashMap<>();

    public static class Band {
        private int minScore;
        private int maxScore;
        private BigDecimal maxAmount;
        private BigDecimal annualRate;
        private int maxTerm;

        public Band() {}

        public Band(int minScore, int maxScore, BigDecimal maxAmount, BigDecimal annualRate, int maxTerm) {
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.maxAmount = maxAmount;
            this.annualRate = annualRate;
            this.maxTerm = maxTerm;
        }

        public boolean covers(int score) { return score >= minScore && score <= maxScore; }

        public int getMinScore() { return minScore; }
        public void setMinScore(int minScore) { this.minScore = minScore; }
        public int getMaxScore() { return maxScore; }
        public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
        public BigDecimal getMaxAmount() { return maxAmount; }
        public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
        public BigDecimal getAnnualRate() { return annualRate; }
        public void setAnnualRate(BigDecimal annualRate) { this.annualRate = annualRate; }
        public int getMaxTerm() { return maxTerm; }
        public void setMaxTerm(int maxTerm) { this.maxTerm = maxTerm; }
    }

    /** The rules exactly as shipped in application.yml — used by unit tests. */
    public static LoanProperties defaults() {
        LoanProperties p = new LoanProperties();
        p.bands.put("LOW", new Band(300, 579, new BigDecimal("30000"), new BigDecimal("28.0"), 12));
        p.bands.put("NORMAL", new Band(580, 719, new BigDecimal("250000"), new BigDecimal("18.0"), 36));
        p.bands.put("HIGH", new Band(720, 850, new BigDecimal("1000000"), new BigDecimal("10.5"), 60));
        return p;
    }

    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
    public int getMinTerm() { return minTerm; }
    public void setMinTerm(int minTerm) { this.minTerm = minTerm; }
    public int getMaxTerm() { return maxTerm; }
    public void setMaxTerm(int maxTerm) { this.maxTerm = maxTerm; }
    public BigDecimal getAffordabilityRatio() { return affordabilityRatio; }
    public void setAffordabilityRatio(BigDecimal affordabilityRatio) { this.affordabilityRatio = affordabilityRatio; }
    public int getDeclineBelowScore() { return declineBelowScore; }
    public void setDeclineBelowScore(int declineBelowScore) { this.declineBelowScore = declineBelowScore; }
    public int getOfferValidDays() { return offerValidDays; }
    public void setOfferValidDays(int offerValidDays) { this.offerValidDays = offerValidDays; }
    public BigDecimal getPenaltyRate() { return penaltyRate; }
    public void setPenaltyRate(BigDecimal penaltyRate) { this.penaltyRate = penaltyRate; }
    public String getBankAccountNo() { return bankAccountNo; }
    public void setBankAccountNo(String bankAccountNo) { this.bankAccountNo = bankAccountNo; }
    public Map<String, Band> getBands() { return bands; }
    public void setBands(Map<String, Band> bands) { this.bands = bands; }
}
