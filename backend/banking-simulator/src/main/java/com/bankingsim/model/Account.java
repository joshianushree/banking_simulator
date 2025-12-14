package com.bankingsim.model;

import com.bankingsim.exception.InsufficientFundsException;
import com.bankingsim.exception.InvalidAmountException;
import com.bankingsim.service.ValidationUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Random;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    private String accountNumber;
    private String holderName;
    private String email;
    private BigDecimal balance;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastActivity;

    private String accountType;

    private String phoneNumber;
    private String gender;
    private String address;

    // LOGIN PIN (hashed)
    private String pin;

    // TRANSACTION PIN (hashed)
    @JsonIgnore
    private String transactionPin;

    private int failedAttempts;
    private boolean isLocked;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lockTime;

    private String status;

    // TX security
    private int txFailedAttempts;
    private boolean txLocked;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime txLockTime;

    // DOB & AGE
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dob;
    private Integer age;

    // Branch + IFSC
    private String branchName;
    private String ifscCode;

    // Govt ID
    private String govtIdType;
    private String govtIdNumber;

    @JsonIgnore
    private byte[] govtIdProof;

    // Deletion flags
    private Integer deletionReq;
    private Integer isDeleted;

    // Loan metadata
    private Integer takenLoan;
    private BigDecimal loanAmount;
    private BigDecimal loanInterestRate;
    private BigDecimal loanTotalDue;
    private Integer autoRepaymentEnabled;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loanTakenDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loanLastPaid;
    private String loanType;
    private String emiPlan;
    private String loanDueCycle;



    // ===================================================================
    // CUSTOM CONSTRUCTOR #1
    // ===================================================================
    public Account(String accountNumber, String holderName, String email, BigDecimal balance) {
        this(
                generateOrKeep(accountNumber),
                holderName,
                email,
                (balance != null ? balance : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN),

                LocalDateTime.now(),       // createdAt
                LocalDateTime.now(),       // lastActivity
                "SAVINGS",                 // type

                null, null, null,          // phone, gender, address

                "0000",                    // pin
                null,                      // transactionPin

                0, false, null,            // failedAttempts, isLocked, lockTime
                "ACTIVE",                  // status

                0, false, null,            // tx failed, locked, tx lock time

                null, null,                // dob, age

                null, null,                // branch, ifsc

                null, null, null,          // govtIdType, govtIdNumber, govtIdProof

                0, 0,                      // deletionReq, isDeleted

                0,                         // takenLoan
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,                         // autoRepaymentEnabled

                null ,null,null, null, null                   // loanLastPaid
        );
    }

    // ===================================================================
    // CUSTOM CONSTRUCTOR #2
    // ===================================================================
    public Account(String accountNumber, String holderName, String email,
                   BigDecimal balance, String accountType, String phoneNumber,
                   String gender, String address, String pin) {

        this(
                generateOrKeep(accountNumber),
                holderName,
                email,
                (balance != null ? balance : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN),

                LocalDateTime.now(),
                LocalDateTime.now(),
                (accountType == null ? "SAVINGS" : accountType.toUpperCase()),

                phoneNumber,
                gender,
                address,

                (pin == null || pin.isBlank()) ? "0000" : pin,
                null,

                0, false, null,
                "ACTIVE",

                0, false, null,

                null, null,

                null, null,

                null, null, null,

                0, 0,

                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,

                null,null,null, null, null
        );
    }

    // ===================================================================
    // HELPERS
    // ===================================================================
    private static String generateOrKeep(String acc) {
        if (acc != null && !acc.isBlank()) return acc;

        String numeric = UUID.randomUUID().toString().replaceAll("\\D", "");
        if (numeric.length() >= 11) return numeric.substring(0, 11);

        long rnd = Math.abs(new Random().nextLong()) % 100000000000L;
        return String.format("%011d", rnd);
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
        if (dob != null) {
            this.age = Period.between(dob, LocalDate.now()).getYears();
        }
    }

    // ===================================================================
    // BUSINESS LOGIC
    // ===================================================================
    public void deposit(BigDecimal amount) throws InvalidAmountException {
        if (!ValidationUtils.isPositiveAmount(amount))
            throw new InvalidAmountException("Deposit amount must be positive.");
        this.balance = this.balance.add(amount);
        this.lastActivity = LocalDateTime.now();
    }

    public void withdraw(BigDecimal amount)
            throws InvalidAmountException, InsufficientFundsException {

        if (!ValidationUtils.isPositiveAmount(amount))
            throw new InvalidAmountException("Withdrawal amount must be positive.");

        if (this.balance.compareTo(amount) < 0)
            throw new InsufficientFundsException("Insufficient funds.");

        this.balance = this.balance.subtract(amount);
        this.lastActivity = LocalDateTime.now();
    }

    // ===================================================================
    // REQUIRED FOR BACKWARD COMPATIBILITY
    // ===================================================================
    @JsonIgnore
    public String getPassword() {
        return this.pin;
    }

    public void setPassword(String password) {
        this.pin = password;
    }
    public int getIsDeleted() { return isDeleted; }

    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }

    public String getEmiPlan() { return emiPlan; }
    public void setEmiPlan(String emiPlan) { this.emiPlan = emiPlan; }

    public String getLoanDueCycle() { return loanDueCycle; }
    public void setLoanDueCycle(String loanDueCycle) { this.loanDueCycle = loanDueCycle; }
}
