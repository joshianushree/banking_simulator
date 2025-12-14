package com.bankingsim.dao;

import com.bankingsim.model.User;
import com.bankingsim.util.BCryptUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class UserDao {

    private final JdbcTemplate jdbcTemplate;
    private static final int MAX_ATTEMPTS = 3;

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------------
    // UPDATE ADMIN DETAILS (now updates only email and phone)
    // -------------------------------------------------------------
    public boolean updateAdmin(String username, User user) {
        try {
            // Only update email and phone to avoid unintentionally changing other fields (status, password, etc.)
            String sql = """
                UPDATE users
                SET email = COALESCE(?, email),
                    phone = COALESCE(?, phone)
                WHERE LOWER(TRIM(username)) = LOWER(TRIM(?))
            """;

            int rows = jdbcTemplate.update(sql,
                    user.getEmail(),
                    user.getPhone(),
                    username
            );

            addAudit("ADMIN_UPDATE", "Updated admin contact details: " + username, "SYSTEM");
            return rows > 0;

        } catch (Exception e) {
            System.err.println("⚠️ Error updating admin: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------
    // EVERYTHING BELOW IS UNCHANGED (original code)
    // -------------------------------------------------------------

    public void createUser(User user) {
        try {
            String pwd = user.getPassword();
            if (pwd == null) pwd = "";
            if (!BCryptUtil.isHashed(pwd)) {
                pwd = BCryptUtil.hashPassword(pwd);
            }

            String sql = """
                INSERT INTO users (username, password, email, phone, role, account_number, status, created_at)
                VALUES (?,?,?,?,?,?,?,?)
            """;

            jdbcTemplate.update(sql,
                    user.getUsername(),
                    pwd,
                    user.getEmail(),
                    user.getPhone(),
                    user.getRole(),
                    user.getAccountNumber(),
                    user.getStatus() == null ? "INACTIVE" : user.getStatus(),
                    user.getCreatedAt() == null ? LocalDateTime.now() : user.getCreatedAt()
            );

            addAudit("CREATE_USER", "Created new user " + user.getUsername(), "SYSTEM");

        } catch (Exception e) {
            System.err.println("⚠️ Error creating user: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void save(User user) {
        createUser(user);
    }

    // ---------------------- VALIDATION ----------------------

    public boolean validateAdmin(String username, String password) {
        try {
            String sql = "SELECT password FROM users WHERE username=? AND role='ADMIN'";
            String stored = jdbcTemplate.queryForObject(sql, String.class, username);
            if (stored == null) return false;

            boolean match = BCryptUtil.verifyPassword(password, stored);
            if (match) {
                if (!BCryptUtil.isHashed(stored)) {
                    setPasswordByUsername(username, password);
                }
                resetFailedAttemptsByUsername(username);
                reactivateIfInactive(username);
                addAudit("LOGIN_SUCCESS", "Admin login successful: " + username, username);
                return true;
            } else {
                addAudit("LOGIN_FAIL", "Admin login failed (wrong password): " + username, username);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateCustomer(String accNo, String pin) {

        Boolean locked = getBooleanValue(
                "SELECT COALESCE(is_locked, FALSE) FROM accounts WHERE account_number=?", accNo);

        if (locked != null && locked) {
            System.out.println("🚫 Account is locked due to too many failed attempts.");
            return false;
        }

        Boolean deleted = getBooleanValue(
                "SELECT COALESCE(is_deleted, FALSE) FROM accounts WHERE account_number=?", accNo);

        if (deleted != null && deleted) {
            System.out.println("❌ Login blocked: Account is deleted.");
            return false;
        }

        try {
            String sql = "SELECT password FROM users WHERE account_number=?";
            String stored = jdbcTemplate.queryForObject(sql, String.class, accNo);

            if (stored != null && BCryptUtil.verifyPassword(pin, stored)) {

                if (!BCryptUtil.isHashed(stored)) {
                    setPinByAccount(accNo, pin);
                }

                resetFailedAttempts(accNo);
                reactivateIfInactiveByAccount(accNo);
                addAudit("LOGIN_SUCCESS", "Customer login successful: " + accNo, accNo);
                return true;

            } else {
                incrementFailedAttempts(accNo);
                addAudit("LOGIN_FAIL", "Customer login failed (invalid PIN): " + accNo, accNo);
                return false;
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error validating customer: " + e.getMessage());
            return false;
        }
    }

    // ---------------------- TRANSACTION PIN VERIFICATION ----------------------

    public boolean verifyTransactionPin(String accNo, String pin) {
        try {

            Boolean txLocked = getBooleanValue(
                    "SELECT COALESCE(tx_locked, FALSE) FROM accounts WHERE account_number=?", accNo);

            if (txLocked != null && txLocked) {
                System.out.println("🚫 Transaction functionality is locked: " + accNo);
                return false;
            }

            String sql = "SELECT transaction_pin FROM accounts WHERE account_number=?";
            String storedHash = jdbcTemplate.queryForObject(sql, String.class, accNo);

            if (storedHash != null && BCryptUtil.verifyPassword(pin, storedHash)) {

                jdbcTemplate.update(
                        "UPDATE accounts SET tx_failed_attempts = 0, tx_locked = FALSE WHERE account_number=?",
                        accNo);

                addAudit("TX_PIN_SUCCESS", "Transaction PIN verified: " + accNo, accNo);
                return true;

            } else {

                Integer attempts = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(tx_failed_attempts,0) FROM accounts WHERE account_number=?",
                        Integer.class, accNo);

                if (attempts == null) attempts = 0;
                attempts++;

                if (attempts >= MAX_ATTEMPTS) {
                    jdbcTemplate.update(
                            "UPDATE accounts SET tx_failed_attempts=?, tx_locked=TRUE WHERE account_number=?",
                            attempts, accNo);

                    addAudit("TX_PIN_LOCK",
                            "Transaction functionality locked after " + attempts + " failed attempts: " + accNo,
                            accNo);

                } else {
                    jdbcTemplate.update(
                            "UPDATE accounts SET tx_failed_attempts=? WHERE account_number=?",
                            attempts, accNo);
                }

                addAudit("TX_PIN_FAIL", "Transaction PIN failed: " + accNo, accNo);
                return false;
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error verifying transaction PIN: " + e.getMessage());
            return false;
        }
    }

    // ---------------------- FAILED ATTEMPTS ----------------------

    public void incrementFailedAttempts(String accNo) {
        try {
            Integer attempts = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(failed_attempts,0) FROM accounts WHERE account_number=?",
                    Integer.class, accNo);

            if (attempts == null) attempts = 0;
            attempts++;

            jdbcTemplate.update("UPDATE accounts SET failed_attempts=? WHERE account_number=?", attempts, accNo);
            jdbcTemplate.update("UPDATE users SET failed_attempts=? WHERE account_number=?", attempts, accNo);

            if (attempts >= MAX_ATTEMPTS) {
                lockAccount(accNo);
                addAudit("LOCK_ACCOUNT",
                        "Account locked after " + MAX_ATTEMPTS + " failed attempts: " + accNo,
                        accNo);
                System.out.println("🚨 Account locked: " + accNo);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error incrementing attempts: " + e.getMessage());
        }
    }

    public void resetFailedAttempts(String accNo) {
        try {
            jdbcTemplate.update(
                    "UPDATE accounts SET failed_attempts=0, is_locked=FALSE WHERE account_number=?", accNo);

            jdbcTemplate.update(
                    "UPDATE users SET failed_attempts=0, is_locked=FALSE WHERE account_number=?", accNo);

        } catch (Exception e) {
            System.err.println("⚠️ Error resetting attempts: " + e.getMessage());
        }
    }

    public void resetFailedAttemptsByUsername(String username) {
        try {
            jdbcTemplate.update(
                    "UPDATE users SET failed_attempts=0, is_locked=FALSE WHERE username=?", username);
        } catch (Exception ignored) {}
    }

    // ---------------------- LOCK / UNLOCK ----------------------

    public void lockAccount(String accNo) {
        try {
            jdbcTemplate.update(
                    "UPDATE accounts SET is_locked=TRUE, lock_time=NOW() WHERE account_number=?", accNo);

            jdbcTemplate.update(
                    "UPDATE users SET is_locked=TRUE WHERE account_number=?", accNo);

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
                        lock_time = NULL
                    WHERE account_number = ?
                    """, accNo);

            jdbcTemplate.update("""
                    UPDATE users
                    SET is_locked = FALSE,
                        failed_attempts = 0
                    WHERE account_number = ?
                    """, accNo);

            addAudit("UNLOCK_ACCOUNT", "Account unlocked: " + accNo, "ADMIN");

        } catch (Exception e) {
            System.err.println("⚠️ Error unlocking account: " + e.getMessage());
        }
    }

    // ---------------------- TRANSACTION PIN LOCK RESET ----------------------

    public boolean resetTransactionPinLock(String accNo) {
        try {
            jdbcTemplate.update(
                    "UPDATE accounts SET tx_failed_attempts=0, tx_locked=FALSE WHERE account_number=?",
                    accNo);

            addAudit("TX_PIN_UNLOCK", "Tx PIN lock reset: " + accNo, accNo);
            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Error resetting tx pin lock: " + e.getMessage());
            return false;
        }
    }

    // ---------------------- SET TRANSACTION PIN ----------------------

    public boolean setTransactionPinByAccount(String accNo, String rawPin) {
        try {
            String hashed = BCryptUtil.hashPassword(rawPin);

            jdbcTemplate.update(
                    "UPDATE accounts SET transaction_pin=?, tx_failed_attempts=0, tx_locked=FALSE WHERE account_number=?",
                    hashed, accNo);

            addAudit("TX_PIN_RESET", "Transaction PIN updated: " + accNo, accNo);
            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Error setting transaction PIN: " + e.getMessage());
            return false;
        }
    }

    // ---------------------- PASSWORD / PIN RESET ----------------------

    public boolean setPasswordByUsername(String username, String rawPassword) {
        try {
            String hashed = BCryptUtil.hashPassword(rawPassword);

            jdbcTemplate.update(
                    "UPDATE users SET password=? WHERE username=?", hashed, username);

            addAudit("PASSWORD_RESET", "Password reset for " + username, username);
            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Error resetting password: " + e.getMessage());
            return false;
        }
    }

    public boolean setPinByAccount(String accNo, String rawPin) {
        try {
            String hashed = BCryptUtil.hashPassword(rawPin);

            jdbcTemplate.update(
                    "UPDATE users SET password=? WHERE account_number=?", hashed, accNo);

            jdbcTemplate.update(
                    "UPDATE accounts SET pin=? WHERE account_number=?", hashed, accNo);

            addAudit("PIN_RESET", "PIN reset for account: " + accNo, accNo);
            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Error resetting PIN: " + e.getMessage());
            return false;
        }
    }

    // ---------------------- AUTO LOGOUT ----------------------

    public void autoLogoutIdleUsers() {
        try {
            String sql = """
                UPDATE users u
                LEFT JOIN accounts a ON u.account_number = a.account_number
                SET u.logout_time = NOW(), u.status='INACTIVE', a.status='INACTIVE'
                WHERE u.login_time IS NOT NULL
                  AND u.logout_time IS NULL
                  AND u.login_time <= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            """;
            jdbcTemplate.update(sql);

            addAudit("AUTO_LOGOUT", "Auto logout idle users", "SYSTEM");

        } catch (Exception e) {
            System.err.println("⚠️ Error auto-logging out users: " + e.getMessage());
        }
    }

    // ---------------------- INACTIVE USERS ----------------------

    public void reactivateIfInactive(String username) {
        try {
            jdbcTemplate.update(
                    "UPDATE users SET status='ACTIVE' WHERE username=? AND status='INACTIVE'",
                    username);
        } catch (Exception ignored) {}
    }

    public void reactivateIfInactiveByAccount(String accNo) {
        try {
            jdbcTemplate.update(
                    "UPDATE users SET status='ACTIVE' WHERE account_number=? AND status='INACTIVE'",
                    accNo);
        } catch (Exception ignored) {}
    }

    // ---------------------- ACTIVITY ----------------------

    public void updateLoginTime(String username) {
        jdbcTemplate.update("""
                UPDATE users
                SET login_time=?, status='ACTIVE'
                WHERE username=?
                """, LocalDateTime.now(), username);

        addAudit("LOGIN", "User logged in: " + username, username);
    }

    public void updateLoginTimeByAccount(String accNo) {
        jdbcTemplate.update("""
                UPDATE users
                SET login_time=?, status='ACTIVE'
                WHERE account_number=?
                """, LocalDateTime.now(), accNo);

        addAudit("LOGIN", "Customer logged in: " + accNo, accNo);
    }

    public void updateLogoutTime(String username) {
        jdbcTemplate.update("""
                UPDATE users
                SET logout_time=?, status='INACTIVE'
                WHERE username=?
                """, LocalDateTime.now(), username);

        addAudit("LOGOUT", "User logged out: " + username, username);
    }

    public void updateLogoutTimeByAccount(String accNo) {
        jdbcTemplate.update("""
                UPDATE users
                SET logout_time=?, status='INACTIVE'
                WHERE account_number=?
                """, LocalDateTime.now(), accNo);

        addAudit("LOGOUT", "Customer logged out: " + accNo, accNo);
    }

    // ---------------------- FINDERS ----------------------

    public User findByUsername(String username) {
        List<User> list = jdbcTemplate.query(
                "SELECT * FROM users WHERE username=?", new UserRowMapper(), username);

        return list.isEmpty() ? null : list.get(0);
    }

    public User findByAccountNumber(String accNo) {
        List<User> list = jdbcTemplate.query(
                "SELECT * FROM users WHERE account_number=?", new UserRowMapper(), accNo);

        return list.isEmpty() ? null : list.get(0);
    }

    // ---------------------- NEW: USER DELETION FLAGS HELPERS ----------------------

    /**
     * Set deletion_req flag for user linked to account (users table).
     */
    public boolean setDeletionRequestedByAccount(String accNo) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE users
                SET deletion_req = 1
                WHERE account_number = ?
            """, accNo);
            addAudit("USER_DELETION_REQ_SET", "User deletion_req set for account: " + accNo, "SYSTEM");
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error setting user deletion_req: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clear deletion_req flag for user linked to account.
     */
    public boolean clearDeletionRequestByAccount(String accNo) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE users
                SET deletion_req = 0
                WHERE account_number = ?
            """, accNo);
            addAudit("USER_DELETION_REQ_CLEARED", "User deletion_req cleared for account: " + accNo, "SYSTEM");
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error clearing user deletion_req: " + e.getMessage());
            return false;
        }
    }

    /**
     * Mark user as deleted (is_deleted = 1) and set status INACTIVE.
     */
    public boolean markUserDeletedByAccount(String accNo) {
        try {
            int rows = jdbcTemplate.update("""
                UPDATE users
                SET is_deleted = 1,
                    deletion_req = 0,
                    status = 'INACTIVE'
                WHERE account_number = ?
            """, accNo);
            addAudit("USER_MARK_DELETED", "User marked deleted for account: " + accNo, "SYSTEM");
            return rows > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error marking user deleted: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get role by account number (ADMIN / CUSTOMER)
     */
    public String getUserRoleByAccount(String accNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT role FROM users WHERE account_number = ?",
                    String.class, accNo);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------- UTIL ----------------------

    private Boolean getBooleanValue(String sql, Object param) {
        try {
            Boolean v = jdbcTemplate.queryForObject(sql, Boolean.class, param);
            return v != null && v;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------- AUDIT ----------------------

    public void addAudit(String event, String description, String actor) {
        try {
            jdbcTemplate.update("""
                INSERT INTO audit_log (event_type, description, actor, timestamp)
                VALUES (?, ?, ?, NOW())
            """, event, description, actor);
        } catch (Exception ignored) {}
    }

    // ---------------------- MAPPER ----------------------

    private static class UserRowMapper implements RowMapper<User> {

        @Override
        public User mapRow(ResultSet rs, int i) throws SQLException {
            User u = new User();

            u.setUserId(rs.getInt("user_id"));
            u.setUsername(rs.getString("username"));
            u.setPassword(rs.getString("password"));
            u.setEmail(rs.getString("email"));
            u.setPhone(rs.getString("phone"));
            u.setRole(rs.getString("role"));
            u.setAccountNumber(rs.getString("account_number"));
            u.setStatus(rs.getString("status"));

            try {
                if (rs.getTimestamp("login_time") != null)
                    u.setLoginTime(rs.getTimestamp("login_time").toLocalDateTime());
            } catch (Exception ignored) {}

            try {
                if (rs.getTimestamp("logout_time") != null)
                    u.setLogoutTime(rs.getTimestamp("logout_time").toLocalDateTime());
            } catch (Exception ignored) {}

            try {
                if (rs.getTimestamp("created_at") != null)
                    u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            } catch (Exception ignored) {}

            try {
                u.setFailedAttempts(rs.getInt("failed_attempts"));
            } catch (Exception ignored) {}

            try {
                u.setLocked(rs.getBoolean("is_locked"));
            } catch (Exception ignored) {}

            try {
                if (rs.getTimestamp("lock_time") != null)
                    u.setLockTime(rs.getTimestamp("lock_time").toLocalDateTime());
            } catch (Exception ignored) {}

            // Optional new fields (deletion_req, is_deleted) read safely if present
            try {
                int deletionReq = rs.getInt("deletion_req");
                if (!rs.wasNull()) {
                    try { java.lang.reflect.Method m = u.getClass().getMethod("setDeletionReq", int.class); m.invoke(u, deletionReq); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            try {
                int isDeleted = rs.getInt("is_deleted");
                if (!rs.wasNull()) {
                    try { java.lang.reflect.Method m = u.getClass().getMethod("setIsDeleted", int.class); m.invoke(u, isDeleted); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            return u;
        }
    }

    // ---------------------- EXISTENCE CHECKS ----------------------

    public boolean accountExists(String accNo) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM accounts WHERE TRIM(account_number)=TRIM(?)",
                    Integer.class, accNo);

            return count != null && count > 0;

        } catch (Exception e) {
            return false;
        }
    }

    public boolean usernameExists(String username) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE LOWER(TRIM(username))=LOWER(?)",
                    Integer.class, username.trim());

            return count != null && count > 0;

        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUserLocked(String username) {
        try {
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT is_locked FROM users WHERE username=?",
                    Boolean.class, username);

            return locked != null && locked;

        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccountLocked(String accNo) {
        try {
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT is_locked FROM accounts WHERE account_number=?",
                    Boolean.class, accNo);

            return locked != null && locked;

        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------- ADMIN MANAGEMENT ----------------------

    public List<User> getAllAdmins() {
        try {
            return jdbcTemplate.query(
                    "SELECT * FROM users WHERE role='ADMIN'",
                    new UserRowMapper());

        } catch (Exception e) {
            System.err.println("⚠️ Error fetching admins: " + e.getMessage());
            return List.of();
        }
    }

    public boolean deleteAdmin(String username) {
        try {
            String accNo = null;

            try {
                accNo = jdbcTemplate.queryForObject(
                        "SELECT account_number FROM users WHERE username=?",
                        String.class, username);
            } catch (Exception ignored) {}

            int rows = jdbcTemplate.update(
                    "DELETE FROM users WHERE username=?", username);

            if (accNo != null && !accNo.isBlank()) {
                jdbcTemplate.update(
                        "DELETE FROM accounts WHERE account_number=?", accNo);
            }

            addAudit("DELETE_ADMIN", "Deleted admin: " + username, "SYSTEM");
            return rows > 0;

        } catch (Exception e) {
            System.err.println("⚠️ Error deleting admin: " + e.getMessage());
            return false;
        }
    }

    public void updateUsername(String accNo, String username) {
        jdbcTemplate.update("""
            UPDATE users
            SET username=?
            WHERE account_number=?
        """, username, accNo);
    }
}
