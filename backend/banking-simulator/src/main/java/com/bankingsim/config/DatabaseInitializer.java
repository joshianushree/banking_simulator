package com.bankingsim.config;

import com.bankingsim.util.BCryptUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ✅ Initializes and upgrades the database schema for the Banking Simulator.
 * ✅ Works cleanly on both new and existing MySQL installations.
 * ✅ Ensures all required tables exist.
 * ✅ Adds missing columns dynamically.
 * ✅ Recalculates ages from DOB on startup and updates database.
 * ⭐ Fully supports Transaction PIN features (transaction_pin, tx_failed_attempts, tx_locked).
 */
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

        // Recalculate ages on every startup and enforce STUDENT account type for minors
        recalculateAgesFromDob();

        System.out.println("✅ Database structure verified and up-to-date.\n");
    }

    // -------------------------------------------------------------------------
    // ACCOUNTS TABLE (must be created BEFORE users because of FK)
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

            -- ⭐ Support for transaction-only lock
            tx_failed_attempts INT DEFAULT 0,
            tx_locked          BOOLEAN DEFAULT FALSE,

            -- ⭐ NEW: Date of Birth and Age
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

        // Ensure new columns exist in older DBs
        addColumnIfMissing("accounts", "transaction_pin", "VARCHAR(100)");
        addColumnIfMissing("accounts", "tx_failed_attempts", "INT DEFAULT 0");
        addColumnIfMissing("accounts", "tx_locked", "BOOLEAN DEFAULT FALSE");

        // DOB and AGE columns
        addColumnIfMissing("accounts", "dob", "DATE");
        addColumnIfMissing("accounts", "age", "INT");
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

        // Add foreign key if missing
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
    }

    // -------------------------------------------------------------------------
    // TRANSACTIONS TABLE
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
    // OTP VERIFICATION TABLE
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
    // AUDIT LOG TABLE
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
    // DEFAULT ADMIN USER
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
            } else {
                System.out.println("ℹ️ Admin account already exists.");
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to create admin: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Recalculate ages from DOB on startup and enforce STUDENT account type for minors
    // -------------------------------------------------------------------------
    private void recalculateAgesFromDob() {
        try {
            // Only update rows where dob is present
            // TIMESTAMPDIFF(YEAR, dob, CURDATE()) calculates age in years
            int updated = jdbcTemplate.update("""
                UPDATE accounts
                SET age = TIMESTAMPDIFF(YEAR, dob, CURDATE())
                WHERE dob IS NOT NULL
            """);
            System.out.println("🔁 Recalculated ages for accounts (rows affected): " + updated);

            // Additionally enforce account_type = 'STUDENT' for minors (age < 18)
            int studentsSet = jdbcTemplate.update("""
                UPDATE accounts
                SET account_type = 'STUDENT'
                WHERE age IS NOT NULL AND age < 18
                  AND (account_type IS NULL OR account_type <> 'STUDENT')
            """);
            if (studentsSet > 0) {
                System.out.println("🔒 Set account_type='STUDENT' for " + studentsSet + " minor account(s).");
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error recalculating ages from DOB: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GENERIC TABLE UPGRADER (Adds Missing Columns)
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

            // remove dummy column
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

    // -------------------------------------------------------------------------
    // SAFELY ADD COLUMN IF MISSING
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

            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + type);
                System.out.println("🆕 Added '" + column + "' to " + table);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Could not add column '" + column + "' to " + table + ": " + e.getMessage());
        }
    }
}
