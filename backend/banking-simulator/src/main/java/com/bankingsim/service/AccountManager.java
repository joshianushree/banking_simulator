package com.bankingsim.service;

import com.bankingsim.dao.AccountDao;
import com.bankingsim.dao.TransactionDao;
import com.bankingsim.model.Account;
import com.bankingsim.model.TransactionRecord;
import com.bankingsim.util.BCryptUtil;
import com.bankingsim.util.PdfGenerator;
import com.bankingsim.util.TemplateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

@Service
public class AccountManager {

    private final AccountDao accountDao;
    private final TransactionDao txDao;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    // ⭐ NEW: Inject OtpService
    @Autowired
    private OtpService otpService;

    @Autowired
    public AccountManager(AccountDao accountDao, TransactionDao txDao, JdbcTemplate jdbcTemplate) {
        this.accountDao = accountDao;
        this.txDao = txDao;
        this.jdbcTemplate = jdbcTemplate;

        PdfGenerator.setAccountDao(accountDao);
    }

    // ⭐ NEW: STATIC IFSC MAPPING (Option 1 Selected)
    private static final Map<String, String> IFSC_MAP = Map.of(
            "Mumbai", "ASTN00MUM01",
            "Bangalore", "ASTN00BLR02",
            "Pune", "ASTN00PUN03",
            "Hyderabad", "ASTN00HYD04"
    );

    // =========================================================================
    // ACCOUNT CREATION — UPDATED WITH BRANCH + IFSC + GOVT ID + DOB/AGE
    // =========================================================================
    public void createAccount(Account a) {

        if (a == null) throw new IllegalArgumentException("Account cannot be null.");

        if (!ValidationUtils.isValidHolderName(a.getHolderName()))
            throw new IllegalArgumentException("Invalid holder name.");

        if (!ValidationUtils.isValidEmail(a.getEmail()))
            throw new IllegalArgumentException("Invalid email.");

        if (!ValidationUtils.isValidPhoneNumber(a.getPhoneNumber()))
            throw new IllegalArgumentException("Invalid phone number.");

        if (!ValidationUtils.isValidGender(a.getGender()))
            throw new IllegalArgumentException("Invalid gender.");

        if (!ValidationUtils.isValidAddress(a.getAddress()))
            throw new IllegalArgumentException("Invalid address.");

        // ⭐ Branch validation
        if (a.getBranchName() == null || a.getBranchName().isBlank())
            throw new IllegalArgumentException("Branch name is required.");

        if (!IFSC_MAP.containsKey(a.getBranchName()))
            throw new IllegalArgumentException("Invalid branch.");

        // ⭐ Auto-set IFSC code
        a.setIfscCode(IFSC_MAP.get(a.getBranchName()));

        // ⭐ Govt ID type required
        if (a.getGovtIdType() == null || a.getGovtIdType().isBlank())
            throw new IllegalArgumentException("Government ID type is required.");

        // ⭐ Govt ID number required
        if (a.getGovtIdNumber() == null || a.getGovtIdNumber().isBlank())
            throw new IllegalArgumentException("Government ID number is required.");

        // ⭐ Govt ID Format Validation
        switch (a.getGovtIdType()) {
            case "Aadhar":
                if (!ValidationUtils.isValidAadhar(a.getGovtIdNumber()))
                    throw new IllegalArgumentException("Invalid Aadhar number (must be 12 digits).");
                break;

            case "PAN":
                if (!ValidationUtils.isValidPAN(a.getGovtIdNumber()))
                    throw new IllegalArgumentException("Invalid PAN format.");
                break;

            case "Voter ID":
                if (!ValidationUtils.isValidVoterId(a.getGovtIdNumber()))
                    throw new IllegalArgumentException("Invalid Voter ID format.");
                break;

            case "Driving License":
                if (!ValidationUtils.isValidDrivingLicense(a.getGovtIdNumber()))
                    throw new IllegalArgumentException("Invalid Driving License format.");
                break;

            default:
                throw new IllegalArgumentException("Unsupported Govt ID type.");
        }

        // ⭐ Govt ID File required
        if (a.getGovtIdProof() == null || a.getGovtIdProof().length == 0)
            throw new IllegalArgumentException("Government ID proof file is required.");

        // ⭐ Govt ID uniqueness check
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE govt_id_number = ?",
                Integer.class,
                a.getGovtIdNumber()
        );
        if (count != null && count > 0)
            throw new IllegalArgumentException("This Govt ID number already exists.");

        // ⭐ DOB required
        if (a.getDob() == null)
            throw new IllegalArgumentException("Date of birth is required.");

        if (a.getDob().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("DOB cannot be in the future.");

        int age = Period.between(a.getDob(), LocalDate.now()).getYears();
        a.setAge(age);

        // ⭐ Auto student account
        if (age < 18) {
            a.setAccountType("STUDENT");
        }

        // PIN validation
        String pin = a.getPassword();
        if (pin != null && !pin.isBlank()) {
            if (!pin.startsWith("$2a$") && !pin.startsWith("$2y$")
                    && !ValidationUtils.isValidPin(pin))
                throw new IllegalArgumentException("Invalid PIN format.");
        }

        if (!ValidationUtils.isValidAccountType(a.getAccountType()))
            throw new IllegalArgumentException("Invalid account type.");

        if (!ValidationUtils.isValidInitialDeposit(a.getAccountType(), a.getBalance()))
            throw new IllegalArgumentException("Minimum balance not met.");

        // ⭐ CREATE ACCOUNT
        accountDao.createAccount(a);

        // ⭐ Send Account Creation Email
        otpService.sendAccountCreationMail(
                a.getEmail(),
                a.getHolderName(),
                a.getAccountNumber(),
                a.getAccountType(),
                a.getBalance(),
                a.getIfscCode()
        );
    }

    // =========================================================================
    // CREATE USER FOR ACCOUNT
    // =========================================================================
    public void createUserForAccount(Account acc, String role) {
        try {
            String username = acc.getHolderName().replaceAll("\\s+", "").toLowerCase();

            String hashedPin = acc.getPassword() != null && (acc.getPassword().startsWith("$2a$") || acc.getPassword().startsWith("$2y$"))
                    ? acc.getPassword()
                    : BCryptUtil.hashPassword(acc.getPassword() == null ? "0000" : acc.getPassword());

            String sql = """
            INSERT INTO users (username, password, email, phone, role, account_number, status)
            VALUES (?, ?, ?, ?, ?, ?, 'INACTIVE')
            """;

            jdbcTemplate.update(sql,
                    username,
                    hashedPin,
                    acc.getEmail(),
                    acc.getPhoneNumber(),
                    role.toUpperCase(),
                    acc.getAccountNumber()
            );

        } catch (Exception e) {
            System.err.println("⚠️ Error creating linked user: " + e.getMessage());
        }
    }

    // =========================================================================
    // TRANSACTIONS (UNCHANGED)
    // =========================================================================

    public void deposit(String accNo, BigDecimal amount, String category) {
        if (!ValidationUtils.isPositiveAmount(amount))
            throw new IllegalArgumentException("Invalid deposit amount.");

        Account a = accountDao.findByAccountNumber(accNo);
        if (a == null) throw new IllegalArgumentException("Account not found.");

        a.setBalance(a.getBalance().add(amount));
        accountDao.updateBalanceAndActivity(a);

        txDao.saveTransaction(new TransactionRecord(
                TransactionRecord.TxType.DEPOSIT, null, accNo, amount, category
        ));

        String msg = TemplateUtil.depositMessage(
                a.getHolderName(),
                accNo,
                amount,
                a.getBalance(),
                category
        );

        notificationService.sendEmail(a.getEmail(), "Deposit Successful", msg);
        notificationService.sendSms(a.getPhoneNumber(), msg);

        reactivateIfInactive(accNo);
    }

    public void withdraw(String accNo, BigDecimal amount, String category) {
        if (!ValidationUtils.isPositiveAmount(amount))
            throw new IllegalArgumentException("Invalid withdrawal amount.");

        Account a = accountDao.findByAccountNumber(accNo);
        if (a == null) throw new IllegalArgumentException("Account not found.");

        if (accountDao.isTransactionLocked(accNo)) {
            throw new IllegalStateException("Transaction functionality is locked for this account. Please reset transaction PIN to proceed.");
        }

        if (amount.compareTo(new BigDecimal("100")) < 0)
            throw new IllegalArgumentException("Minimum ₹100 balance required.");

        if (a.getBalance().compareTo(amount) < 0)
            throw new IllegalArgumentException("Insufficient funds.");

        a.setBalance(a.getBalance().subtract(amount));
        accountDao.updateBalanceAndActivity(a);

        txDao.saveTransaction(new TransactionRecord(
                TransactionRecord.TxType.WITHDRAW, accNo, null, amount, category
        ));

        String msg = TemplateUtil.withdrawalMessage(
                a.getHolderName(),
                accNo,
                amount,
                a.getBalance(),
                category
        );

        notificationService.sendEmail(a.getEmail(), "Withdrawal Successful", msg);
        notificationService.sendSms(a.getPhoneNumber(), msg);

        reactivateIfInactive(accNo);
    }

    public void transfer(String fromAccNo, String toAccNo, BigDecimal amount, String category, String ifsc) {

        if (!ValidationUtils.isPositiveAmount(amount))
            throw new IllegalArgumentException("Invalid transfer amount.");

        if (accountDao.isTransactionLocked(fromAccNo)) {
            throw new IllegalStateException(
                    "Transaction functionality is locked for this account. Please reset transaction PIN to proceed."
            );
        }

        Account from = accountDao.findByAccountNumber(fromAccNo);
        Account to = accountDao.findByAccountNumber(toAccNo);

        if (from == null)
            throw new IllegalArgumentException("Sender account not found.");
        if (to == null)
            throw new IllegalArgumentException("Recipient account not found.");
        if ( to.getIsDeleted() == 1) {
            throw new IllegalArgumentException("Cannot transfer to this account. The recipient account is deleted.");
        }

        if (fromAccNo.equals(toAccNo))
            throw new IllegalArgumentException("Cannot transfer to same account.");

        // ⭐ NEW — IFSC VALIDATION
        if (to.getIfscCode() == null || !to.getIfscCode().equalsIgnoreCase(ifsc)) {
            throw new IllegalArgumentException("Invalid IFSC code for the recipient account.");
        }

        if (amount.compareTo(new BigDecimal("100")) < 0)
            throw new IllegalArgumentException("Minimum ₹100 required after transfer.");

        if (from.getBalance().compareTo(amount) < 0)
            throw new IllegalArgumentException("Insufficient funds.");

        // Deduct & Add
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountDao.updateBalanceAndActivity(from);
        accountDao.updateBalanceAndActivity(to);

        txDao.saveTransaction(new TransactionRecord(
                TransactionRecord.TxType.TRANSFER, fromAccNo, toAccNo, amount, category
        ));

        // Notifications
        String senderMsg = TemplateUtil.transferSenderMessage(
                from.getHolderName(), fromAccNo, toAccNo, amount, from.getBalance(), category
        );

        notificationService.sendEmail(from.getEmail(), "Transfer Successful", senderMsg);
        notificationService.sendSms(from.getPhoneNumber(), senderMsg);

        String receiverMsg = TemplateUtil.transferReceiverMessage(
                to.getHolderName(), fromAccNo, toAccNo, amount, to.getBalance(), category
        );

        notificationService.sendEmail(to.getEmail(), "Amount Received", receiverMsg);
        notificationService.sendSms(to.getPhoneNumber(), receiverMsg);

        reactivateIfInactive(fromAccNo);
        reactivateIfInactive(toAccNo);
    }

    // =========================================================================
    // ROLLBACK / LOGOUT / INACTIVITY (UNCHANGED)
    // =========================================================================
    public void rollbackTransaction(String txId, String adminUser) {
        boolean ok = txDao.rollbackTransaction(txId, adminUser);
        System.out.println(ok ? "Rollback successful" : "Rollback failed.");
    }

    public void autoLogoutInactiveUsers() {
        try {
            String sql = """
                UPDATE users
                SET status='INACTIVE', logout_time=NOW()
                WHERE status='ACTIVE' AND login_time < DATE_SUB(NOW(), INTERVAL 1 HOUR)
            """;
            jdbcTemplate.update(sql);
        } catch (Exception ignored) {}
    }

    public void markInactiveAccounts() {
        try {
            String sql = """
                UPDATE accounts a
                JOIN users u ON a.account_number = u.account_number
                SET a.status='INACTIVE', u.status='INACTIVE'
                WHERE a.last_activity < DATE_SUB(NOW(), INTERVAL 6 MONTH)
                  AND a.status='ACTIVE'
            """;
            jdbcTemplate.update(sql);
        } catch (Exception ignored) {}
    }

    public void reactivateIfInactive(String accNo) {
        try {
            jdbcTemplate.update("UPDATE accounts SET status='ACTIVE' WHERE account_number=? AND status='INACTIVE'", accNo);
            jdbcTemplate.update("UPDATE users SET status='ACTIVE' WHERE account_number=? AND status='INACTIVE'", accNo);
        } catch (Exception ignored) {}
    }
    public List<Account> listAccountsByBranch(String branch) {
        return accountDao.listAccountsByBranch(branch);
    }


    // =========================================================================
    // HELPERS
    // =========================================================================
    public boolean accountExists(String accNo) {
        try {
            if (accNo == null) return false;

            String trimmed = accNo.trim();
            if (trimmed.isEmpty()) return false;

            String sql = "SELECT COUNT(*) FROM accounts WHERE account_number=?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, trimmed);

            return count != null && count > 0;

        } catch (Exception e) {
            System.err.println("⚠️ Error checking account existence: " + e.getMessage());
            return false;
        }
    }

    public Account findAccountByNumber(String accNo) {
        return accountDao.findByAccountNumber(accNo);
    }

    public BigDecimal getBalance(String accNo) {
        Account a = findAccountByNumber(accNo);
        if (a == null) throw new IllegalArgumentException("Account not found.");
        return a.getBalance();
    }

    public List<Account> listAllAccounts() {
        return accountDao.listAllAccounts();
    }

    public List<Account> getLockedAccounts() {
        return accountDao.getLockedAccounts();
    }

    // =========================================================================
    // CONTACT
    // =========================================================================
    public void updateContactInfo(String accNo, String email, String phone) {
        accountDao.updateContactInfo(accNo, email, phone);
    }

    public void updateBalanceAndActivity(Account account) {
        accountDao.updateBalanceAndActivity(account);
    }

    // =========================================================================
    // ADMIN ACTIONS
    // =========================================================================
    public void deleteAccount(String accNo) {
        accountDao.deleteAccount(accNo);
    }

    public void unlockAccount(String accNo) {
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

        System.out.println("✅ Account unlocked: " + accNo);
    }

    // =========================================================================
    // PDF EXPORT
    // =========================================================================
    public void generateAccountsPdf() {
        List<Account> accounts = accountDao.listAllAccounts();
        if (!accounts.isEmpty()) PdfGenerator.generateAccountsPdf(accounts);
    }

    public void generateTransactionsPdf() {
        List<TransactionRecord> transactions = txDao.getAllTransactions();
        if (!transactions.isEmpty()) PdfGenerator.generateTransactionsPdf(transactions);
    }

    public void generateMiniStatementPdf(String accNo) {
        List<TransactionRecord> tx = txDao.getTransactionsByAccount(accNo);
        if (!tx.isEmpty()) PdfGenerator.generateMiniStatementPdf(accNo, tx);
    }

    public void updateCustomerDetails(Account account) {

        // ⭐ NEW: Prevent modifying account_type if age < 18
        if (account.getAge() != null && account.getAge() < 18) {
            account.setAccountType("STUDENT");
        }

        accountDao.updateCustomerDetails(account);
    }

    // =========================================================================
    // NEW: IFSC VALIDATION HELPER
    // =========================================================================
    /**
     * Validates that the given account number exists and that the stored IFSC
     * matches the provided IFSC (case-insensitive, trimmed).
     *
     * Returns true only when the account exists and the IFSC matches.
     */
    public boolean isAccountAndIfscValid(String accNo, String ifsc) {
        try {
            if (accNo == null || ifsc == null) return false;
            String trimmedAcc = accNo.trim();
            String trimmedIfsc = ifsc.trim();
            if (trimmedAcc.isEmpty() || trimmedIfsc.isEmpty()) return false;

            Account acc = accountDao.findByAccountNumber(trimmedAcc);
            if (acc == null) return false;

            String storedIfsc = acc.getIfscCode();
            if (storedIfsc == null) return false;

            return storedIfsc.trim().equalsIgnoreCase(trimmedIfsc);
        } catch (Exception e) {
            System.err.println("⚠️ Error validating account+IFSC: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------
// SOFT DELETE / RESTORE HELPERS
// ---------------------------

    /**
     * Soft-deletes a customer account:
     * - sets accounts.status = 'DELETED', is_locked = TRUE, failed_attempts = 0, lock_time = NOW(), last_activity = NOW()
     * - sets users.status = 'DELETED', is_locked = TRUE, failed_attempts = 0, logout_time = NOW()
     * - inserts an audit_log entry
     *
     * Returns true on success.
     */
    public boolean softDeleteCustomerAccount(String accNo, String reason, String actor) {
        try {
            // Backup step already handled by caller optionally (mini-statement)
            // 1) Update accounts table
            jdbcTemplate.update("""
            UPDATE accounts
            SET status = 'DELETED',
                is_locked = TRUE,
                failed_attempts = 0,
                lock_time = NOW(),
                last_activity = NOW()
            WHERE account_number = ?
        """, accNo);

            // 2) Update users table linked to this account
            jdbcTemplate.update("""
            UPDATE users
            SET status = 'DELETED',
                is_locked = TRUE,
                failed_attempts = 0,
                logout_time = NOW()
            WHERE account_number = ?
        """, accNo);

            // 3) Audit log
            jdbcTemplate.update("""
            INSERT INTO audit_log (event_type, description, actor, timestamp)
            VALUES (?, ?, ?, NOW())
        """, "ACCOUNT_SOFT_DELETED", "Soft-deleted account: " + accNo + " Reason: " + (reason == null ? "" : reason), actor == null ? "SYSTEM" : actor);

            // regenerate reports (optional)
            try {
                generateAccountsPdf();
            } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
            System.err.println("⚠️ Error soft-deleting account: " + e.getMessage());
            return false;
        }
    }

    /**
     * Restores a previously soft-deleted account:
     * - sets accounts.status = 'ACTIVE', is_locked = FALSE, lock_time = NULL
     * - sets users.status = 'ACTIVE', is_locked = FALSE
     *
     * Returns true on success.
     */
    public boolean restoreSoftDeletedAccount(String accNo, String actor) {
        try {
            jdbcTemplate.update("""
            UPDATE accounts
            SET status = 'ACTIVE',
                is_locked = FALSE,
                failed_attempts = 0,
                lock_time = NULL,
                last_activity = NOW()
            WHERE account_number = ?
        """, accNo);

            jdbcTemplate.update("""
            UPDATE users
            SET status = 'ACTIVE',
                is_locked = FALSE,
                failed_attempts = 0,
                logout_time = NULL
            WHERE account_number = ?
        """, accNo);

            jdbcTemplate.update("""
            INSERT INTO audit_log (event_type, description, actor, timestamp)
            VALUES (?, ?, ?, NOW())
        """, "ACCOUNT_RESTORED", "Restored account: " + accNo, actor == null ? "SYSTEM" : actor);

            try {
                generateAccountsPdf();
            } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
            System.err.println("⚠️ Error restoring account: " + e.getMessage());
            return false;
        }
    }

}
