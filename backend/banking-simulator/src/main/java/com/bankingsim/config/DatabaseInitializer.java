package com.bankingsim.config;

import com.bankingsim.util.BCryptUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        System.out.println("\n🔍 Checking and initializing database tables...");

        ensureAccountsTable();
        ensureUsersTable();
        ensureTransactionsTable();
        ensureOtpVerificationTable();
        ensureAuditLogTable();
        ensureDefaultAdminUser();

        recalculateAgesFromDob();

        ensureDeletionRequestsTable();
        ensureLoanRequestsTable();

        System.out.println("✅ Database structure verified and up-to-date.\n");
    }

    // -------------------------------------------------------------------------
    // ACCOUNTS TABLE
    // -------------------------------------------------------------------------
    private void ensureAccountsTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS `accounts` (
            account_number     VARCHAR(20) PRIMARY KEY,
            holder_name        VARCHAR(100) NOT NULL,
            email              VARCHAR(100),
            phone_number       VARCHAR(15),
            gender             VARCHAR(20) DEFAULT 'OTHER',
            address            VARCHAR(255),
            balance            DECIMAL(15,2) DEFAULT 0.00,
            account_type       VARCHAR(50) DEFAULT 'SAVINGS',
            pin                VARCHAR(100) NOT NULL,
            transaction_pin    VARCHAR(100),
            status             VARCHAR(16) DEFAULT 'ACTIVE',
            created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
            last_activity      DATETIME NULL,
            failed_attempts    INT DEFAULT 0,
            is_locked          BOOLEAN DEFAULT FALSE,
            lock_time          DATETIME NULL,

            tx_failed_attempts INT DEFAULT 0,
            tx_locked          BOOLEAN DEFAULT FALSE,

            dob                DATE NULL,
            age                INT NULL
        )
        """;

        try {
            jdbcTemplate.execute(sql);
            System.out.println("✅ Ensured table: accounts");
        } catch (Exception e) {
            System.err.println("⚠️ Error creating accounts: " + e.getMessage());
        }

        // STANDARD COLUMNS
        addColumnIfMissing("accounts", "deletion_req", "TINYINT(1) DEFAULT 0");
        addColumnIfMissing("accounts", "is_deleted", "TINYINT(1) DEFAULT 0");
        addColumnIfMissing("accounts", "taken_loan", "TINYINT(1) DEFAULT 0");
        addColumnIfMissing("accounts", "loan_amount", "DECIMAL(15,2) DEFAULT 0.00");
        addColumnIfMissing("accounts", "loan_interest_rate", "DECIMAL(6,3) DEFAULT 0.000");
        addColumnIfMissing("accounts", "loan_total_due", "DECIMAL(15,2) DEFAULT 0.00");
        addColumnIfMissing("accounts", "auto_repayment_enabled", "TINYINT(1) DEFAULT 0");
        // ⭐ Branch + IFSC (required by account creation form)
        addColumnIfMissing("accounts", "branch_name", "VARCHAR(100)");
        addColumnIfMissing("accounts", "ifsc_code", "VARCHAR(20)");
        // ⭐ REQUIRED FOR ACCOUNT CREATION — ADDED NOW
        addColumnIfMissing("accounts", "govt_id_type", "VARCHAR(50)");
        addColumnIfMissing("accounts", "govt_id_number", "VARCHAR(50)");
        addColumnIfMissing("accounts", "govt_id_proof", "LONGBLOB");
        addColumnIfMissing("accounts", "loan_taken_date", "DATETIME NULL");
        addColumnIfMissing("accounts", "loan_last_paid", "DATETIME NULL");
        addColumnIfMissing("accounts", "emi_plan", "VARCHAR(32)");
        addColumnIfMissing("accounts", "loan_type", "VARCHAR(64)");
        addColumnIfMissing("accounts", "loan_due_cycle", "VARCHAR(32)");

    }

    // -------------------------------------------------------------------------
    // USERS TABLE
    // -------------------------------------------------------------------------
    private void ensureUsersTable() {
        String table = "users";

        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("user_id", "INT AUTO_INCREMENT PRIMARY KEY");
        cols.put("username", "VARCHAR(50) NOT NULL");
        cols.put("password", "VARCHAR(100) NOT NULL");
        cols.put("email", "VARCHAR(100)");
        cols.put("phone", "VARCHAR(15)");
        cols.put("role", "VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'");
        cols.put("account_number", "VARCHAR(20)");
        cols.put("login_time", "DATETIME NULL");
        cols.put("logout_time", "DATETIME NULL");
        cols.put("status", "VARCHAR(16) DEFAULT 'INACTIVE'");
        cols.put("created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        cols.put("failed_attempts", "INT DEFAULT 0");
        cols.put("is_locked", "BOOLEAN DEFAULT FALSE");

        createOrUpdateTable(table, cols);

        try {
            jdbcTemplate.execute("""
                ALTER TABLE users
                ADD CONSTRAINT fk_users_accounts
                FOREIGN KEY (account_number)
                REFERENCES accounts(account_number)
                ON DELETE SET NULL
                ON UPDATE CASCADE
            """);
        } catch (Exception ignored) {}

        addColumnIfMissing("users", "deletion_req", "TINYINT(1) DEFAULT 0");
        addColumnIfMissing("users", "is_deleted", "TINYINT(1) DEFAULT 0");
    }

    // -------------------------------------------------------------------------
    // TRANSACTIONS
    // -------------------------------------------------------------------------
    private void ensureTransactionsTable() {
        try {
            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS `transactions` (
                tx_id         VARCHAR(36) PRIMARY KEY,
                tx_type       VARCHAR(20),
                from_account  VARCHAR(20),
                to_account    VARCHAR(20),
                amount        DECIMAL(15,2) NOT NULL,
                category      VARCHAR(50) DEFAULT 'General',
                status        VARCHAR(20) DEFAULT 'SUCCESS',
                rolled_back_by VARCHAR(50),
                created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,

                CONSTRAINT fk_from_acc FOREIGN KEY (from_account)
                    REFERENCES accounts(account_number)
                    ON DELETE SET NULL ON UPDATE CASCADE,

                CONSTRAINT fk_to_acc FOREIGN KEY (to_account)
                    REFERENCES accounts(account_number)
                    ON DELETE SET NULL ON UPDATE CASCADE
            )
            """);
            System.out.println("✅ Ensured table: transactions");
        } catch (Exception e) {
            System.err.println("⚠️ Error transactions table: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // OTP TABLE
    // -------------------------------------------------------------------------
    private void ensureOtpVerificationTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `otp_verification` (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_identifier VARCHAR(100) NOT NULL,
                    otp_code VARCHAR(10) NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    expires_at DATETIME NOT NULL,
                    verified BOOLEAN DEFAULT FALSE
                )
            """);
            System.out.println("✅ Ensured table: otp_verification");
        } catch (Exception e) {
            System.err.println("⚠️ Error otp_verification: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // AUDIT LOG
    // -------------------------------------------------------------------------
    private void ensureAuditLogTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `audit_log` (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_type VARCHAR(50),
                    description TEXT,
                    actor VARCHAR(50),
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            System.out.println("✅ Ensured table: audit_log");
        } catch (Exception e) {
            System.err.println("⚠️ Error audit_log: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // DELETION REQUESTS TABLE
    // -------------------------------------------------------------------------
    private void ensureDeletionRequestsTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS deletion_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    account_number VARCHAR(32) NOT NULL,
                    requester_name VARCHAR(255),
                    requester_email VARCHAR(255),
                    requester_phone VARCHAR(32),
                    ifsc_code VARCHAR(20),
                    reason TEXT,
                    requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    status VARCHAR(32) DEFAULT 'PENDING',
                    admin_comment TEXT,
                    processed_at DATETIME NULL,
                    processed_by VARCHAR(100)
                )
            """);
            System.out.println("✅ Ensured table: deletion_requests");
        } catch (Exception e) {
            System.err.println("⚠️ Error creating deletion_requests: " + e.getMessage());
        }

        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_deletion_account ON deletion_requests(account_number)");
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // LOAN REQUESTS TABLE
    // -------------------------------------------------------------------------
    private void ensureLoanRequestsTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    
                                account_number VARCHAR(32) NOT NULL,
                    
                                loan_type VARCHAR(64) NOT NULL,
                    
                                -- Loan Amount
                                requested_amount DECIMAL(15,2) NOT NULL,
                    
                                -- Interest Rate for the loan
                                interest_rate DECIMAL(6,3),
                    
                                -- EMI Plan: MONTHLY, QUARTERLY, YEARLY
                                emi_plan VARCHAR(32),
                    
                                -- Govt ID details
                                govt_id_number VARCHAR(128) NOT NULL,
                                govt_id_proof LONGBLOB NOT NULL,
                    
                                -- Whether user accepted T&C
                                terms_accepted BOOLEAN DEFAULT FALSE,
                    
                                -- Timestamps
                                requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                processed_at DATETIME NULL,
                    
                                -- Status management
                                status VARCHAR(32) DEFAULT 'PENDING',
                                admin_comment TEXT,
                                processed_by VARCHAR(100)
                )
            """);
            System.out.println("✅ Ensured table: loan_requests");
        } catch (Exception e) {
            System.err.println("⚠️ Error creating loan_requests: " + e.getMessage());
        }

        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_loan_account ON loan_requests(account_number)");
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // DEFAULT ADMIN
    // -------------------------------------------------------------------------
    private void ensureDefaultAdminUser() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE role='ADMIN'",
                    Integer.class);

            if (count == null || count == 0) {

                String hashed = BCryptUtil.hashPassword("admin123");

                jdbcTemplate.update("""
                    INSERT INTO users (username, password, email, phone, role, status, created_at)
                    VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE', NOW())
                """, "admin", hashed, "admin@bank.com", "+911234567890");

                System.out.println("⭐ Default admin created (username: admin / password: admin123)");
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to create admin: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // AGE RECALCULATION
    // -------------------------------------------------------------------------
    private void recalculateAgesFromDob() {
        try {
            jdbcTemplate.update("""
                UPDATE accounts
                SET age = TIMESTAMPDIFF(YEAR, dob, CURDATE())
                WHERE dob IS NOT NULL
            """);

            jdbcTemplate.update("""
                UPDATE accounts
                SET account_type = 'STUDENT'
                WHERE age < 18 AND age IS NOT NULL AND account_type <> 'STUDENT'
            """);

        } catch (Exception e) {
            System.err.println("⚠️ Error recalculating ages: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // UTIL: ADD MISSING COLUMN
    // -------------------------------------------------------------------------
    private void addColumnIfMissing(String table, String column, String type) {
        try {
            Integer exists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = ?
                AND COLUMN_NAME = ?
            """, Integer.class, table, column);

            if (exists != null && exists == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + type);
                System.out.println("🆕 Added '" + column + "' to " + table);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Could not add column '" + column + "' to " + table + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // TABLE CREATOR + SAFE COLUMN ADD
    // -------------------------------------------------------------------------
    private void createOrUpdateTable(String table, Map<String, String> expectedColumns) {

        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `" + table + "` (dummy_col INT)");
        } catch (Exception ignored) {}

        try {
            var existingCols = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME 
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = ?
            """, table);

            for (var entry : expectedColumns.entrySet()) {

                boolean exists = existingCols.stream().anyMatch(
                        c -> c.get("COLUMN_NAME").toString().equalsIgnoreCase(entry.getKey()));

                if (!exists) {
                    jdbcTemplate.execute(
                            "ALTER TABLE `" + table + "` ADD COLUMN `" + entry.getKey() + "` " + entry.getValue());
                    System.out.println("🆕 Added missing column '" + entry.getKey() + "' to " + table);
                }
            }

            Integer dummyExists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = ?
                AND COLUMN_NAME = 'dummy_col'
            """, Integer.class, table);

            if (dummyExists != null && dummyExists > 0) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP COLUMN dummy_col");
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error updating table " + table + ": " + e.getMessage());
        }
    }
}
