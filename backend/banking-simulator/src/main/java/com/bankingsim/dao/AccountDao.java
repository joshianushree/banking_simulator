package com.bankingsim.dao;

import com.bankingsim.model.Account;
import com.bankingsim.util.BCryptUtil;
import com.bankingsim.util.PdfGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DAO layer for managing Account persistence using Spring's JdbcTemplate.
 *
 * Updated:
 *  - Added helpers for deletion request workflow: markDeletionRequested, approveDeletion, rejectDeletion
 *  - Added loan-field setters: applyLoanMetadata, clearLoanMetadata, enableAutoRepayment
 *  - RowMapper updated to map new flags/loan columns (deletion_req, is_deleted, taken_loan, loan_amount, loan_interest_rate, loan_total_due, auto_repayment_enabled)
 *
 * Note: Core behavior (createAccount, updateBalanceAndActivity, PIN handling, lock/unlock, rollback, PDF generation, etc.)
 * remains unchanged from the original file.
 */
@Repository
public class AccountDao {

    private final JdbcTemplate jdbcTemplate;

    public AccountDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================================================
    // ACCOUNT CREATION WITH DOB + AGE + BRANCH + IFSC + GOVT ID
    // ======================================================
    public void createAccount(Account a) {
        if (a == null) {
            System.out.println("❌ Account details cannot be null.");
            return;
        }

        // Auto-generate account number if missing
        if (a.getAccountNumber() == null || a.getAccountNumber().isBlank()) {
            a.setAccountNumber(String.valueOf(System.currentTimeMillis()).substring(2, 13));
        }

        String accNum = a.getAccountNumber();
        if (!accNum.matches("\\d{11}")) {
            System.out.println("❌ Invalid account number format (must be 11 digits).");
            return;
        }

        if (findByAccountNumber(accNum) != null) {
            System.out.println("⚠️ Account already exists: " + accNum);
            return;
        }

        // 🔐 Login PIN hashing
        String loginPinToStore = a.getPassword();
        if (loginPinToStore == null || loginPinToStore.isBlank()) loginPinToStore = "0000";

        if (!loginPinToStore.startsWith("$2a$") && !loginPinToStore.startsWith("$2y$"))
            loginPinToStore = BCryptUtil.hashPassword(loginPinToStore);

        // 🔐 TX PIN hashing
        String txPinToStore = a.getTransactionPin();
        if (txPinToStore != null && !txPinToStore.isBlank()) {
            if (!txPinToStore.startsWith("$2a$") && !txPinToStore.startsWith("$2y$")) {
                txPinToStore = BCryptUtil.hashPassword(txPinToStore);
            }
        }

        // ⭐ Ensure AGE always matches DOB
        if (a.getDob() != null) {
            int age = java.time.Period.between(a.getDob(), LocalDate.now()).getYears();
            a.setAge(age);
        }

        // ⭐ FINAL INSERT QUERY — INCLUDING NEW FIELDS
        String sql = """
            INSERT INTO accounts
            (account_number, holder_name, email, balance, created_at,
             account_type, phone_number, gender, address, pin, transaction_pin,
             last_activity, status, failed_attempts, is_locked, lock_time,
             tx_failed_attempts, tx_locked, dob, age,
             branch_name, ifsc_code, govt_id_type, govt_id_number, govt_id_proof)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 
                    'ACTIVE', 0, FALSE, NULL, 0, FALSE, ?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.update(sql,
                a.getAccountNumber(),
                a.getHolderName(),
                a.getEmail(),
                a.getBalance(),
                a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.now(),
                a.getAccountType(),
                a.getPhoneNumber(),
                a.getGender(),
                a.getAddress(),
                loginPinToStore,
                txPinToStore,
                LocalDateTime.now(),
                a.getDob(),
                a.getAge(),
                a.getBranchName(),
                a.getIfscCode(),
                a.getGovtIdType(),
                a.getGovtIdNumber(),
                a.getGovtIdProof()
        );

        addAudit("CREATE_ACCOUNT", "Created new account: " + accNum, a.getHolderName());
        System.out.println("✅ Account created successfully: " + accNum);

        generateAccountsReport();
    }

    // ======================================================
    // READ
    // ======================================================
    public Account findByAccountNumber(String accNum) {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        List<Account> results = jdbcTemplate.query(sql, new AccountRowMapper(), accNum);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Account> listAllAccounts() {
        String sql = "SELECT * FROM accounts ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, new AccountRowMapper());
    }

    public List<Account> listAccountsByBranch(String branch) {
        String sql = "SELECT * FROM accounts WHERE branch_name = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, new AccountRowMapper(), branch);
    }


    // ======================================================
    // UPDATES
    // ======================================================
    public void updateBalanceAndActivity(Account account) {
        try {
            String sql = "UPDATE accounts SET balance = ?, last_activity = ? WHERE account_number = ?";
            jdbcTemplate.update(sql, account.getBalance(), LocalDateTime.now(), account.getAccountNumber());

            addAudit("BALANCE_UPDATE",
                    "Updated balance for account: " + account.getAccountNumber() + " → ₹" + account.getBalance(),
                    account.getHolderName());

        } catch (Exception e) {
            System.err.println("⚠️ Error updating balance: " + e.getMessage());
        }
    }

    public void updateAccountStatus(Account account) {
        try {
            jdbcTemplate.update(
                    "UPDATE accounts SET status = ? WHERE account_number = ?",
                    account.getStatus(), account.getAccountNumber()
            );

            addAudit("STATUS_UPDATE", "Account status changed to " + account.getStatus(), account.getHolderName());
            generateAccountsReport();

        } catch (Exception e) {
            System.err.println("⚠️ Error updating status: " + e.getMessage());
        }
    }

    // ======================================================
    // CONTACT UPDATE
    // ======================================================
    public void updateContactInfo(String accNum, String email, String phone) {
        try {
            jdbcTemplate.update("""
                UPDATE accounts
                SET email = ?, phone_number = ?, last_activity = ?
                WHERE account_number = ?
            """, email, phone, LocalDateTime.now(), accNum);

            jdbcTemplate.update("""
                UPDATE users
                SET email = ?, phone = ?
                WHERE account_number = ?
            """, email, phone, accNum);

            addAudit("UPDATE_CONTACT", "Updated contact info for account: " + accNum, "ADMIN");

        } catch (Exception e) {
            System.err.println("⚠️ Error updating contact info: " + e.getMessage());
        }
    }

    // ======================================================
    // DELETE ACCOUNT
    // ======================================================
    public boolean deleteAccount(String accNum) {
        Account existing = findByAccountNumber(accNum);
        if (existing == null) return false;

        int rows = jdbcTemplate.update("DELETE FROM accounts WHERE account_number = ?", accNum);

        if (rows > 0) {
            addAudit("DELETE_ACCOUNT", "Deleted account: " + accNum, "SYSTEM");
            generateAccountsReport();
        }
        return rows > 0;
    }

    // New: mark that a user has requested deletion (sets deletion_req in accounts and users)
    public boolean markDeletionRequested(String accNum) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE accounts
                SET deletion_req = 1,
                    last_activity = ?
                WHERE account_number = ?
            """, LocalDateTime.now(), accNum);

            jdbcTemplate.update("""
                UPDATE users
                SET deletion_req = 1
                WHERE account_number = ?
            """, accNum);

            addAudit("DELETION_REQUEST", "Deletion requested for account: " + accNum, accNum);
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error marking deletion requested: " + e.getMessage());
            return false;
        }
    }

    // New: approve deletion (set is_deleted flag and clear deletion_req); adminName used for audit
    public boolean approveDeletion(String accNum, String adminName) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE accounts
                SET is_deleted = 1,
                    deletion_req = 0,
                    status = 'INACTIVE',
                    last_activity = ?
                WHERE account_number = ?
            """, LocalDateTime.now(), accNum);

            jdbcTemplate.update("""
                UPDATE users
                SET is_deleted = 1,
                    deletion_req = 0,
                    status = 'INACTIVE'
                WHERE account_number = ?
            """, accNum);

            addAudit("DELETION_APPROVED", "Admin " + adminName + " approved deletion for: " + accNum, adminName);
            generateAccountsReport();
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error approving deletion: " + e.getMessage());
            return false;
        }
    }

    // New: reject deletion (clear deletion_req and store admin comment in audit; actual comment storage should be in deletion_requests table)
    public boolean rejectDeletion(String accNum, String adminName, String adminComment) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE accounts
                SET deletion_req = 0,
                    last_activity = ?
                WHERE account_number = ?
            """, LocalDateTime.now(), accNum);

            jdbcTemplate.update("""
                UPDATE users
                SET deletion_req = 0
                WHERE account_number = ?
            """, accNum);

            addAudit("DELETION_REJECTED", "Admin " + adminName + " rejected deletion for: " + accNum + " — " + adminComment, adminName);
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error rejecting deletion: " + e.getMessage());
            return false;
        }
    }

    // ======================================================
    // SECURITY (LOGIN + TX PIN)
    // ======================================================
    public int getFailedAttempts(String accNum) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT failed_attempts FROM accounts WHERE account_number = ?",
                    Integer.class, accNum
            );
        } catch (Exception e) {
            return 0;
        }
    }

    public void setFailedAttempts(String accNum, int attempts) {
        jdbcTemplate.update("UPDATE accounts SET failed_attempts = ? WHERE account_number = ?", attempts, accNum);
    }

    public void lockAccount(String accNum) {
        try {
            String role = jdbcTemplate.queryForObject(
                    "SELECT role FROM users WHERE account_number = ?",
                    String.class, accNum);

            if ("ADMIN".equalsIgnoreCase(role)) {
                jdbcTemplate.update("UPDATE accounts SET failed_attempts = 0 WHERE account_number = ?", accNum);
                return;
            }

            jdbcTemplate.update("""
                UPDATE accounts
                SET is_locked = TRUE, lock_time = NOW(), status='INACTIVE'
                WHERE account_number = ?
            """, accNum);

            jdbcTemplate.update("""
                UPDATE users
                SET is_locked = TRUE, failed_attempts = 3
                WHERE account_number = ?
            """, accNum);

            addAudit("LOCK_ACCOUNT", "Locked account after 3 failed logins: " + accNum, "SYSTEM");

        } catch (Exception e) {
            System.err.println("⚠️ Error locking account: " + e.getMessage());
        }
    }

    public void unlockAccount(String accNo) {
        try {
            jdbcTemplate.update("""
            UPDATE accounts
            SET is_locked = FALSE,
                failed_attempts = 0,
                lock_time = NULL,
                status = 'ACTIVE'
            WHERE account_number = ?
        """, accNo);

            jdbcTemplate.update("""
            UPDATE users
            SET is_locked = FALSE,
                failed_attempts = 0
            WHERE account_number = ?
        """, accNo);

            addAudit("UNLOCK_ACCOUNT", "Unlocked account: " + accNo, "ADMIN");

        } catch (Exception e) {
            System.err.println("⚠️ Error unlocking account " + e.getMessage());
        }
    }

    // ======================================================
    // TRANSACTION-ONLY LOCK HELPERS
    // ======================================================
    public int getTxFailedAttempts(String accNum) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COALESCE(tx_failed_attempts,0) FROM accounts WHERE account_number = ?",
                    Integer.class, accNum
            );
        } catch (Exception e) {
            return 0;
        }
    }

    public void setTxFailedAttempts(String accNum, int attempts) {
        jdbcTemplate.update("UPDATE accounts SET tx_failed_attempts = ? WHERE account_number = ?", attempts, accNum);
    }

    public boolean isTransactionLocked(String accNum) {
        try {
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(tx_locked, FALSE) FROM accounts WHERE account_number = ?",
                    Boolean.class, accNum
            );
            return locked != null && locked;
        } catch (Exception e) {
            return false;
        }
    }

    public void lockTransactionForAccount(String accNum) {
        try {
            jdbcTemplate.update("UPDATE accounts SET tx_locked=TRUE WHERE account_number = ?", accNum);
            addAudit("TX_LOCK", "Transaction operations locked for account: " + accNum, "SYSTEM");
        } catch (Exception e) {
            System.err.println("⚠️ Error locking transaction functionality: " + e.getMessage());
        }
    }

    public void unlockTransactionForAccount(String accNum) {
        try {
            jdbcTemplate.update("UPDATE accounts SET tx_locked=FALSE, tx_failed_attempts=0 WHERE account_number = ?", accNum);
            addAudit("TX_UNLOCK", "Transaction operations unlocked for account: " + accNum, "SYSTEM");
        } catch (Exception e) {
            System.err.println("⚠️ Error unlocking transaction functionality: " + e.getMessage());
        }
    }

    // ======================================================
    // TRANSACTION PIN UPDATE
    // ======================================================
    public boolean setTransactionPin(String accNum, String rawPin) {
        if (accNum == null || accNum.isBlank() || rawPin == null || rawPin.isBlank()) {
            return false;
        }
        try {
            String toStore = rawPin;
            if (!toStore.startsWith("$2a$") && !toStore.startsWith("$2y$")) {
                toStore = BCryptUtil.hashPassword(toStore);
            }

            jdbcTemplate.update("""
                UPDATE accounts
                SET transaction_pin = ?, tx_failed_attempts = 0, tx_locked = FALSE, last_activity = ?
                WHERE account_number = ?
            """, toStore, LocalDateTime.now(), accNum);

            addAudit("UPDATE_TX_PIN", "Updated transaction PIN for account: " + accNum, "SYSTEM");
            return true;
        } catch (Exception e) {
            System.err.println("⚠️ Error setting transaction PIN: " + e.getMessage());
            return false;
        }
    }

    // ======================================================
    // ROLLBACK
    // ======================================================
    public boolean reverseLastTransaction(String accNo, double amount, String adminUser) {
        try {
            jdbcTemplate.update("""
                UPDATE accounts
                SET balance = balance + ?, last_activity = ?
                WHERE account_number = ?
            """, amount, LocalDateTime.now(), accNo);

            addAudit("ROLLBACK",
                    "Rolled back transaction for account " + accNo + " (₹" + amount + ")",
                    adminUser);

            generateAccountsReport();
            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Rollback failed: " + e.getMessage());
            return false;
        }
    }

    // ======================================================
    // LOAN METADATA HELPERS (AccountDao only updates fields; LoanService handles transactions)
    // ======================================================

    /**
     * Apply loan metadata to the account record.
     * This method updates only account metadata (taken_loan, loan_amount, loan_interest_rate, loan_total_due)
     * and last_activity. It does NOT credit the account balance — that must be done in LoanService using TransactionDao.
     *
     * @param accNum       account number
     * @param loanAmount   principal amount
     * @param interestRate annual interest rate (percentage, e.g. 7.5)
     * @param totalDue     total due (principal + interest) calculated by LoanService
     * @return true if update succeeded
     */
    public boolean applyLoanMetadata(String accNum, java.math.BigDecimal loanAmount, java.math.BigDecimal interestRate, java.math.BigDecimal totalDue) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE accounts
                SET taken_loan = 1,
                    loan_amount = ?,
                    loan_interest_rate = ?,
                    loan_total_due = ?,
                    last_activity = ?
                WHERE account_number = ?
            """, loanAmount, interestRate, totalDue, LocalDateTime.now(), accNum);

            addAudit("LOAN_METADATA_SET", "Loan metadata applied to account: " + accNum + " amount=" + loanAmount, "SYSTEM");
            generateAccountsReport();
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error applying loan metadata: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clear loan metadata (for use in rollbacks or loan cancellations).
     */
    public boolean clearLoanMetadata(String accNum) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE accounts
                SET taken_loan = 0,
                    loan_amount = 0.00,
                    loan_interest_rate = 0.000,
                    loan_total_due = 0.00,
                    auto_repayment_enabled = 0,
                    last_activity = ?
                WHERE account_number = ?
            """, LocalDateTime.now(), accNum);

            addAudit("LOAN_METADATA_CLEARED", "Cleared loan metadata for account: " + accNum, "SYSTEM");
            generateAccountsReport();
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error clearing loan metadata: " + e.getMessage());
            return false;
        }
    }

    /**
     * Enable or disable auto repayment flag on account.
     */
    public boolean enableAutoRepayment(String accNum, boolean enable) {
        try {
            int flag = enable ? 1 : 0;
            int rows = jdbcTemplate.update("""
                UPDATE accounts
                SET auto_repayment_enabled = ?,
                    last_activity = ?
                WHERE account_number = ?
            """, flag, LocalDateTime.now(), accNum);

            addAudit("AUTO_REPAYMENT_" + (enable ? "ENABLED" : "DISABLED"),
                    "Auto repayment " + (enable ? "enabled" : "disabled") + " for account: " + accNum, "SYSTEM");
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error toggling auto repayment: " + e.getMessage());
            return false;
        }
    }

    // ======================================================
    // PDF
    // ======================================================
    public void generateAccountsReport() {
        try {
            PdfGenerator.generateAccountsPdf(listAllAccounts());
        } catch (Exception ignored) {}
    }

    // ======================================================
    // AUDIT
    // ======================================================
    private void addAudit(String event, String desc, String actor) {
        try {
            jdbcTemplate.update("""
                INSERT INTO audit_log (event_type, description, actor, timestamp)
                VALUES (?, ?, ?, NOW())
            """, event, desc, actor);
        } catch (Exception ignored) {}
    }

    // ======================================================
    // ROW MAPPER — Includes New Fields
    // ======================================================
    private static class AccountRowMapper implements RowMapper<Account> {
        @Override
        public Account mapRow(ResultSet rs, int rowNum) throws SQLException {

            Account a = new Account(
                    rs.getString("account_number"),
                    rs.getString("holder_name"),
                    rs.getString("email"),
                    rs.getBigDecimal("balance"),
                    rs.getString("account_type"),
                    rs.getString("phone_number"),
                    rs.getString("gender"),
                    rs.getString("address"),
                    rs.getString("pin")
            );

            // ---------------------
            // STANDARD FIELDS
            // ---------------------
            a.setTransactionPin(rs.getString("transaction_pin"));
            a.setStatus(rs.getString("status"));
            a.setFailedAttempts(rs.getInt("failed_attempts"));
            a.setLocked(rs.getBoolean("is_locked"));

            try { a.setTxFailedAttempts(rs.getInt("tx_failed_attempts")); } catch (Exception ignored) {}
            try { a.setTxLocked(rs.getBoolean("tx_locked")); } catch (Exception ignored) {}

            if (rs.getTimestamp("created_at") != null)
                a.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

            if (rs.getTimestamp("last_activity") != null)
                a.setLastActivity(rs.getTimestamp("last_activity").toLocalDateTime());

            if (rs.getTimestamp("lock_time") != null)
                a.setLockTime(rs.getTimestamp("lock_time").toLocalDateTime());

            // ---------------------
            // DOB + AGE
            // ---------------------
            try {
                if (rs.getDate("dob") != null)
                    a.setDob(rs.getDate("dob").toLocalDate());
            } catch (Exception ignored) {}

            try {
                int age = rs.getInt("age");
                if (!rs.wasNull()) a.setAge(age);
            } catch (Exception ignored) {}

            // ---------------------
            // IFSC + BRANCH
            // ---------------------
            try { a.setBranchName(rs.getString("branch_name")); } catch (Exception ignored) {}
            try { a.setIfscCode(rs.getString("ifsc_code")); } catch (Exception ignored) {}

            // ---------------------
            // GOVT ID
            // ---------------------
            try { a.setGovtIdType(rs.getString("govt_id_type")); } catch (Exception ignored) {}
            try { a.setGovtIdNumber(rs.getString("govt_id_number")); } catch (Exception ignored) {}
            try { a.setGovtIdProof(rs.getBytes("govt_id_proof")); } catch (Exception ignored) {}

            // ---------------------
            // DELETION FLAGS
            // ---------------------
            try {
                a.setDeletionReq(rs.getInt("deletion_req"));
            } catch (Exception ignored) {}

            try {
                a.setIsDeleted(rs.getInt("is_deleted"));
            } catch (Exception ignored) {}

            // ---------------------
            // LOAN FIELDS (FIXED)
            // ---------------------
            try { a.setTakenLoan(rs.getInt("taken_loan")); } catch (Exception ignored) {}
            try { a.setLoanAmount(rs.getBigDecimal("loan_amount")); } catch (Exception ignored) {}
            try { a.setLoanInterestRate(rs.getBigDecimal("loan_interest_rate")); } catch (Exception ignored) {}
            try { a.setLoanTotalDue(rs.getBigDecimal("loan_total_due")); } catch (Exception ignored) {}
            try { a.setAutoRepaymentEnabled(rs.getInt("auto_repayment_enabled")); } catch (Exception ignored) {}

            try {
                if (rs.getTimestamp("loan_taken_date") != null)
                    a.setLoanTakenDate(rs.getTimestamp("loan_taken_date").toLocalDateTime());
            } catch (Exception ignored) {}

            try {
                if (rs.getTimestamp("loan_last_paid") != null)
                    a.setLoanLastPaid(rs.getTimestamp("loan_last_paid").toLocalDateTime());
            } catch (Exception ignored) {}

            // NEW: loan_type, emi_plan, loan_due_cycle
            try { a.setLoanType(rs.getString("loan_type")); } catch (Exception ignored) {}
            try { a.setEmiPlan(rs.getString("emi_plan")); } catch (Exception ignored) {}
            try { a.setLoanDueCycle(rs.getString("loan_due_cycle")); } catch (Exception ignored) {}

            return a;
        }
    }

    // ======================================================
    // LOCKED ACCOUNTS LIST
    // ======================================================
    public List<Account> getLockedAccounts() {
        return jdbcTemplate.query("SELECT * FROM accounts WHERE is_locked = TRUE",
                new AccountRowMapper());
    }

    // ======================================================
    // CONTACT VERIFICATION
    // ======================================================
    public boolean emailOrPhoneMatches(String accNo, String contact) {
        try {
            Account a = findByAccountNumber(accNo);
            if (a == null || contact == null) return false;

            String trimmed = contact.trim();
            if (trimmed.isBlank()) return false;

            String storedEmail = a.getEmail() != null ? a.getEmail().trim() : "";
            String storedPhone = a.getPhoneNumber() != null ? a.getPhoneNumber().trim() : "";

            return trimmed.equalsIgnoreCase(storedEmail)
                    || trimmed.equalsIgnoreCase(storedPhone);

        } catch (Exception e) {
            return false;
        }
    }

    // ======================================================
    // ADMIN DELETE ACCOUNT
    // ======================================================
    public boolean deleteAccountByNumber(String accNo) {
        try {
            int rows = jdbcTemplate.update("DELETE FROM accounts WHERE account_number = ?", accNo);
            if (rows > 0) {
                addAudit("DELETE_ADMIN_ACCOUNT", "Deleted admin-linked account: " + accNo, "SYSTEM");
            }
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error deleting admin-linked account: " + e.getMessage());
            return false;
        }
    }

    // ======================================================
    // CUSTOMER DETAIL UPDATE
    // ======================================================
    public void updateCustomerDetails(Account a) {
        String sql = """
        UPDATE accounts
        SET holder_name = ?, 
            address = ?, 
            gender = ?, 
            account_type = ?, 
            last_activity = NOW()
        WHERE account_number = ?
        """;

        jdbcTemplate.update(sql,
                a.getHolderName(),
                a.getAddress(),
                a.getGender(),
                a.getAccountType(),
                a.getAccountNumber()
        );

        addAudit("UPDATE_CUSTOMER_DETAILS",
                "Updated customer details for: " + a.getAccountNumber(),
                a.getHolderName());
    }
}
