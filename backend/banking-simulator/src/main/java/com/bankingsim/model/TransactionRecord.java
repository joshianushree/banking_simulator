package com.bankingsim.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

public class TransactionRecord {

    public enum TxType {
        DEPOSIT,
        WITHDRAW,
        TRANSFER,
        ACCOUNT_CLOSED,
        ROLLBACK,
        LOAN_CREDIT,
        LOAN_REPAYMENT,
    }

    private String txId;
    private TxType txType;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private String category;
    private LocalDateTime createdAt;
    private String status;

    private String timestamp;

    public TransactionRecord() {
        this.txId = generateTransactionId();
        this.createdAt = LocalDateTime.now();
        this.category = "General";
        this.status = "SUCCESS";
    }

    public TransactionRecord(TxType txType, String fromAccount, String toAccount, BigDecimal amount) {
        this(txType, fromAccount, toAccount, amount, "General");
    }

    public TransactionRecord(TxType txType, String fromAccount, String toAccount, BigDecimal amount, String category) {
        this.txId = generateTransactionId();
        this.txType = Objects.requireNonNull(txType, "Transaction type cannot be null");
        this.fromAccount = normalizeAccount(fromAccount);
        this.toAccount = normalizeAccount(toAccount);
        this.amount = validateAmount(amount);
        this.category = (category == null || category.isBlank()) ? "General" : category.trim();
        this.createdAt = LocalDateTime.now();
        this.status = "SUCCESS";
        this.timestamp = this.createdAt.toString();
    }

    private BigDecimal validateAmount(BigDecimal amt) {
        if (amt == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        if (amt.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Transaction amount must be non-negative");
        }
        return amt.setScale(2, RoundingMode.HALF_EVEN);
    }

    private String normalizeAccount(String acc) {
        return (acc == null || acc.isBlank() || "-".equals(acc)) ? "-" : acc.trim();
    }

    private String generateTransactionId() {
        String shortId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "TXN-" + datePart + "-" + shortId;
    }

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public TxType getTxType() { return txType; }
    public void setTxType(TxType txType) { this.txType = txType; }

    public String getFromAccount() { return fromAccount; }
    public void setFromAccount(String fromAccount) { this.fromAccount = normalizeAccount(fromAccount); }

    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = normalizeAccount(toAccount); }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = validateAmount(amount); }

    public String getCategory() { return category; }
    public void setCategory(String category) {
        this.category = (category == null || category.isBlank()) ? "General" : category.trim();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = (status == null ? "SUCCESS" : status); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        this.timestamp = (createdAt != null) ? createdAt.toString() : null;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getFormattedDate() {
        if (createdAt == null) return "-";
        return createdAt.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    // ✔ SAFE NEW HELPER (no existing functionality changed)
    public String getFormattedTxType() {
        if (txType == null) return "-";
        String s = txType.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public String getSummaryLine() {
        return String.format(
                "%-18s | %-13s | ₹%-10s | From: %-11s | To: %-11s | Category: %-10s | Date: %s",
                txId,
                txType == null ? "UNKNOWN" : txType,
                amount == null ? "0.00" : amount,
                fromAccount,
                toAccount,
                category,
                getFormattedDate()
        );
    }

    @Override
    public String toString() {
        return String.format("""
                Transaction ID: %s
                Type: %s
                Amount: ₹%s
                From: %s → To: %s
                Category: %s
                Date: %s
                """,
                txId,
                txType == null ? "UNKNOWN" : txType,
                amount == null ? "0.00" : amount,
                fromAccount,
                toAccount,
                category,
                getFormattedDate());
    }
}
