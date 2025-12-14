package com.bankingsim.model;

import java.math.BigDecimal;

public class DeletionRequest {

    private long id;
    private String accountNumber;
    private String holderName;
    private String email;
    private String phone;
    private String ifsc;
    private String reason;

    // Loan details (kept as double to avoid breaking your existing code)
    private boolean hasLoan;
    private double loanAmount;
    private double loanTotalDue;
    private String loanType;
    private String emiPlan;

    private String status = "PENDING"; // PENDING / APPROVED / REJECTED
    private String admin;
    private String adminComment;

    public DeletionRequest() {}

    public DeletionRequest(long id, String accountNumber, String holderName,
                           String email, String phone, String ifsc, String reason) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.email = email;
        this.phone = phone;
        this.ifsc = ifsc;
        this.reason = reason;
    }

    // ===== GETTERS & SETTERS =====

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isHasLoan() { return hasLoan; }
    public void setHasLoan(boolean hasLoan) { this.hasLoan = hasLoan; }

    public double getLoanAmount() { return loanAmount; }
    public void setLoanAmount(double loanAmount) { this.loanAmount = loanAmount; }

    public double getLoanTotalDue() { return loanTotalDue; }
    public void setLoanTotalDue(double loanTotalDue) { this.loanTotalDue = loanTotalDue; }

    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }

    public String getEmiPlan() { return emiPlan; }
    public void setEmiPlan(String emiPlan) { this.emiPlan = emiPlan; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdmin() { return admin; }
    public void setAdmin(String admin) { this.admin = admin; }

    public String getAdminComment() { return adminComment; }
    public void setAdminComment(String adminComment) { this.adminComment = adminComment; }

    // ===== OPTIONAL HELPERS (NO BREAKING CHANGE) =====

    public void setLoanAmount(BigDecimal amount) {
        this.loanAmount = amount != null ? amount.doubleValue() : 0.0;
    }

    public void setLoanTotalDue(BigDecimal due) {
        this.loanTotalDue = due != null ? due.doubleValue() : 0.0;
    }

}
