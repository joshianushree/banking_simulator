package com.bankingsim.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Model representing a loan request submitted by a customer.
 * Matches LoanDao + LoanController expected fields exactly.
 */
public class LoanRequest {

    private Long id;
    private String accountNumber;
    private BigDecimal requestedAmount;
    private BigDecimal interestRate;
    private String loanType;
    private String emiPlan;
    private String govtIdNumber;
    private byte[] govtIdProof;

    private String status;  // PENDING / APPROVED / REJECTED / CLOSED
    private String adminComment;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private boolean termsAccepted;


    public LoanRequest() {}

    // getters / setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }

    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }

    public String getEmiPlan() { return emiPlan; }
    public void setEmiPlan(String emiPlan) { this.emiPlan = emiPlan; }

    public String getGovtIdNumber() { return govtIdNumber; }
    public void setGovtIdNumber(String govtIdNumber) { this.govtIdNumber = govtIdNumber; }

    public byte[] getGovtIdProof() { return govtIdProof; }
    public void setGovtIdProof(byte[] govtIdProof) { this.govtIdProof = govtIdProof; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminComment() { return adminComment; }
    public void setAdminComment(String adminComment) { this.adminComment = adminComment; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }
}

