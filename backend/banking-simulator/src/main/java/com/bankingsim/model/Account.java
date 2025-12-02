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

    private String pin;                 // Login PIN (hashed)

    @JsonIgnore
    private String transactionPin;      // Hashed transaction PIN

    private int failedAttempts;         // LOGIN failed attempts
    private boolean isLocked;           // LOGIN locked

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lockTime;     // LOGIN lock timestamp

    private String status;

    // ⭐ NEW FIELDS — Transaction-only security
    private int txFailedAttempts;
    private boolean txLocked;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime txLockTime;

    // ⭐ NEW FIELDS — DOB and AGE
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dob;
    private Integer age;

    // ⭐ NEW FIELDS — BRANCH + IFSC
    private String branchName;
    private String ifscCode;

    // ⭐ NEW FIELDS — Govt ID
    private String govtIdType;
    private String govtIdNumber;

    @JsonIgnore
    private byte[] govtIdProof;     // PDF / JPG / PNG bytes

    // --------------------------------------------------------------
    // CUSTOM CONSTRUCTORS
    // --------------------------------------------------------------

    public Account(String accountNumber, String holderName, String email, BigDecimal balance) {
        this(
                generateOrKeep(accountNumber),
                holderName,
                email,
                (balance != null ? balance : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN),
                LocalDateTime.now(),
                LocalDateTime.now(),
                "SAVINGS",
                null,
                null,
                null,
                "0000",
                null,
                0,
                false,
                null,
                "ACTIVE",
                0,
                false,
                null,
                null,   // dob
                null,   // age
                null,   // branch
                null,   // ifsc
                null,   // govtIdType
                null,   // govtIdNumber
                null    // govtIdProof
        );
    }

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
                0,
                false,
                null,
                "ACTIVE",
                0,
                false,
                null,
                null,   // dob
                null,   // age
                null,   // branch
                null,   // ifsc
                null,   // govtIdType
                null,   // govtIdNumber
                null    // govtIdProof
        );
    }

    // --------------------------------------------------------------
    // HELPERS
    // --------------------------------------------------------------

    private static String generateOrKeep(String acc) {
        if (acc != null && !acc.isBlank()) return acc;
        String numeric = UUID.randomUUID().toString().replaceAll("\\D", "");
        if (numeric.length() >= 11) return numeric.substring(0, 11);

        long rnd = Math.abs(new Random().nextLong()) % 100000000000L;
        return String.format("%011d", rnd);
    }

    public void setPhone(String phone) {
        this.phoneNumber = phone;
    }

    public String getPassword() {
        return this.pin;
    }

    public void setPassword(String password) {
        this.pin = password;
    }

    // ⭐ Auto-calculate age whenever DOB is set
    public void setDob(LocalDate dob) {
        this.dob = dob;
        if (dob != null) {
            this.age = Period.between(dob, LocalDate.now()).getYears();
        }
    }

    // --------------------------------------------------------------
    // BUSINESS LOGIC — DO NOT MODIFY ORIGINAL BEHAVIOR
    // --------------------------------------------------------------

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
}
