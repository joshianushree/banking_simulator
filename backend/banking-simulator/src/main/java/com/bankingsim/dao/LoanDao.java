package com.bankingsim.dao;

import com.bankingsim.model.LoanRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class LoanDao {

    private final JdbcTemplate jdbcTemplate;

    public LoanDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureLoanRequestsTable();
    }

    // ======================================================================
    // CREATE TABLE (FINAL SCHEMA) - matches columns you listed
    // ======================================================================
    private void ensureLoanRequestsTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    account_number VARCHAR(32),
                    loan_type VARCHAR(64),
                    requested_amount DECIMAL(15,2),
                    interest_rate DECIMAL(6,3),
                    emi_plan VARCHAR(32),
                    govt_id_number VARCHAR(128),
                    govt_id_proof LONGBLOB,
                    terms_accepted TINYINT(1) DEFAULT 0,
                    requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    processed_at DATETIME NULL,
                    status VARCHAR(32) DEFAULT 'PENDING',
                    admin_comment TEXT,
                    processed_by VARCHAR(100)
                )
            """);
        } catch (Exception e) {
            System.err.println("⚠️ ensureLoanRequestsTable failed: " + e.getMessage());
        }
    }

    // ======================================================================
    // SAVE NEW REQUEST
    // ======================================================================
    public long saveLoanRequest(LoanRequest req) {
        try {
            // allow nulls for some fields; use Timestamp for requested_at if provided
            Timestamp requestedTs = req.getRequestedAt() == null ? Timestamp.valueOf(LocalDateTime.now())
                    : Timestamp.valueOf(req.getRequestedAt());

            jdbcTemplate.update("""
                INSERT INTO loan_requests
                (account_number, loan_type, requested_amount, interest_rate,
                 emi_plan, govt_id_number, govt_id_proof, terms_accepted, requested_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """,
                    req.getAccountNumber(),
                    req.getLoanType(),
                    req.getRequestedAmount(),
                    req.getInterestRate(),
                    req.getEmiPlan(),
                    req.getGovtIdNumber(),
                    req.getGovtIdProof(),
                    req.isTermsAccepted() ? 1 : 0,
                    requestedTs
            );

            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            addAudit("LOAN_REQ_SAVED", "Loan request saved id=" + id + " acc=" + req.getAccountNumber(), req.getAccountNumber());
            return id == null ? -1L : id;

        } catch (Exception e) {
            System.err.println("⚠ saveLoanRequest failed: " + e.getMessage());
            return -1L;
        }
    }

    // ======================================================================
    // GET ALL PENDING REQUESTS
    // ======================================================================
    public List<Map<String, Object>> getAllPendingRequests() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT * FROM loan_requests
                WHERE status = 'PENDING'
                ORDER BY requested_at ASC
            """);
        } catch (Exception e) {
            System.err.println("⚠ getAllPendingRequests failed: " + e.getMessage());
            return List.of();
        }
    }

    // ======================================================================
    // GET SINGLE REQUEST
    // ======================================================================
    public LoanRequest getLoanRequestById(long id) {
        try {
            return jdbcTemplate.queryForObject("""
                SELECT * FROM loan_requests WHERE id = ?
            """, (rs, row) -> {
                LoanRequest r = new LoanRequest();
                r.setId(rs.getLong("id"));
                r.setAccountNumber(rs.getString("account_number"));
                r.setLoanType(rs.getString("loan_type"));

                // requested_amount may be null? treat safely
                try { r.setRequestedAmount(rs.getBigDecimal("requested_amount")); } catch (SQLException ignored) {}

                try { r.setInterestRate(rs.getBigDecimal("interest_rate")); } catch (SQLException ignored) {}

                r.setEmiPlan(rs.getString("emi_plan"));
                r.setGovtIdNumber(rs.getString("govt_id_number"));

                try {
                    Blob b = rs.getBlob("govt_id_proof");
                    if (b != null) r.setGovtIdProof(b.getBytes(1, (int) b.length()));
                } catch (SQLException ignored) {}

                // terms_accepted stored as tinyint - map to boolean
                try {
                    int ta = rs.getInt("terms_accepted");
                    r.setTermsAccepted(ta == 1);
                } catch (SQLException ignored) {}

                r.setStatus(rs.getString("status"));
                r.setAdminComment(rs.getString("admin_comment"));
                r.setProcessedBy(rs.getString("processed_by"));

                Timestamp tReq = rs.getTimestamp("requested_at");
                if (tReq != null) r.setRequestedAt(tReq.toLocalDateTime());

                Timestamp tProc = rs.getTimestamp("processed_at");
                if (tProc != null) r.setProcessedAt(tProc.toLocalDateTime());

                return r;
            }, id);
        } catch (Exception e) {
            // return null if not found or mapping failed
            System.err.println("⚠ getLoanRequestById failed for id=" + id + " : " + e.getMessage());
            return null;
        }
    }

    // ======================================================================
    // APPROVE REQUEST (ONLY UPDATES loan_requests TABLE metadata)
    // ======================================================================
    public boolean approveLoanRequest(long reqId, String adminUser, String comment) {
        try {
            jdbcTemplate.update("""
                UPDATE loan_requests
                SET status = 'APPROVED',
                    admin_comment = COALESCE(NULLIF(?, ''), admin_comment),
                    processed_at = NOW(),
                    processed_by = ? 
                WHERE id = ?
            """, comment, adminUser, reqId);

            addAudit("LOAN_REQUEST_APPROVE", "Approved loan request id=" + reqId + " by " + adminUser, adminUser);
            return true;
        } catch (Exception e) {
            System.err.println("⚠ approveLoanRequest failed: " + e.getMessage());
            return false;
        }
    }

    // ======================================================================
    // APPLY LOAN TO ACCOUNT (AFTER APPROVAL) - credits account and sets loan fields
    // ======================================================================
    public boolean applyLoanToAccount(LoanRequest req) {
        try {
            BigDecimal principal = req.getRequestedAmount() == null ? BigDecimal.ZERO : req.getRequestedAmount();
            BigDecimal rate = req.getInterestRate() == null ? BigDecimal.ZERO : req.getInterestRate();
            // totalDue = principal + (principal * rate / 100)
            BigDecimal totalDue = principal.add(principal.multiply(rate).divide(BigDecimal.valueOf(100)));

            // update account loan metadata and credit the principal to balance
            jdbcTemplate.update("""
                UPDATE accounts
                SET taken_loan = 1,
                    loan_amount = ?,
                    loan_interest_rate = ?,
                    loan_total_due = ?,
                    loan_taken_date = NOW(),
                    loan_last_paid = NULL,
                    balance = COALESCE(balance,0) + ?
                WHERE account_number = ?
            """, principal, rate, totalDue, principal, req.getAccountNumber());

            addAudit("LOAN_APPLY_ACCOUNT", "Applied loan to account " + req.getAccountNumber(), "SYSTEM");
            return true;
        } catch (Exception e) {
            System.err.println("⚠ applyLoanToAccount failed: " + e.getMessage());
            return false;
        }
    }

    // ======================================================================
    // REJECT REQUEST
    // ======================================================================
    public boolean rejectLoan(long reqId, String adminUser, String comment) {
        try {
            jdbcTemplate.update("""
                UPDATE loan_requests
                SET status = 'REJECTED',
                    admin_comment = COALESCE(NULLIF(?, ''), admin_comment),
                    processed_at = NOW(),
                    processed_by = ?
                WHERE id = ?
            """, comment, adminUser, reqId);

            // Clear loan fields on account if any (safe operation)
            LoanRequest req = getLoanRequestById(reqId);
            if (req != null && req.getAccountNumber() != null) {
                jdbcTemplate.update("""
                    UPDATE accounts
                    SET taken_loan = 0,
                        loan_amount = 0,
                        loan_interest_rate = 0,
                        loan_total_due = 0,
                        loan_taken_date = NULL,
                        loan_last_paid = NULL
                    WHERE account_number = ?
                """, req.getAccountNumber());
            }

            addAudit("LOAN_REQUEST_REJECT", "Rejected loan request id=" + reqId + " by " + adminUser, adminUser);
            return true;
        } catch (Exception e) {
            System.err.println("⚠ rejectLoan failed: " + e.getMessage());
            return false;
        }
    }

    // ======================================================================
    // Analytics helpers
    // ======================================================================
    public BigDecimal getAverageBalance(String accNo) {
        try {
            BigDecimal v = jdbcTemplate.queryForObject("""
                SELECT COALESCE(AVG(balance),0) FROM accounts WHERE account_number = ?
            """, BigDecimal.class, accNo);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            System.err.println("⚠ getAverageBalance failed: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getTotalDepositsLast6Months(String accNo) {
        try {
            BigDecimal v = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM transactions
                WHERE (tx_type = 'DEPOSIT' OR tx_type = 'TRANSFER')
                  AND to_account = ?
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
            """, BigDecimal.class, accNo);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            System.err.println("⚠ getTotalDepositsLast6Months failed: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ======================================================================
    // Outstanding amount for account (reads accounts.loan_total_due)
    // ======================================================================
    public BigDecimal getOutstandingAmount(String accNo) {
        try {
            BigDecimal v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(loan_total_due, 0) FROM accounts WHERE account_number = ?",
                    BigDecimal.class,
                    accNo
            );
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            System.err.println("⚠️ getOutstandingAmount failed: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ======================================================================
    // Close loan fully: clear account loan fields and mark request closed
    // ======================================================================
    public boolean closeLoan(String accNo) {
        try {
            // Reset loan fields only (NO TRANSACTION INSERT HERE)
            jdbcTemplate.update("""
            UPDATE accounts
            SET loan_total_due = 0,
                taken_loan = 0,
                loan_amount = 0,
                loan_interest_rate = 0,
                loan_taken_date = NULL,
                loan_last_paid = NOW()
            WHERE account_number = ?
        """, accNo);

            jdbcTemplate.update("""
            UPDATE loan_requests
            SET status = 'CLOSED',
                processed_at = NOW()
            WHERE account_number = ? AND status = 'APPROVED'
        """, accNo);

            addAudit("LOAN_CLOSED", "Loan closed for " + accNo, "SYSTEM");
            return true;

        } catch (Exception e) {
            System.err.println("⚠️ closeLoan failed: " + e.getMessage());
            return false;
        }
    }

    // ======================================================================
    // Toggle auto repayment flag stored in accounts table
    // ======================================================================
    public boolean setAutoRepayment(String accNo, boolean enabled) {
        try {
            jdbcTemplate.update(
                    "UPDATE accounts SET auto_repayment_enabled = ? WHERE account_number = ?",
                    enabled ? 1 : 0,
                    accNo
            );

            addAudit(enabled ? "AUTO_REPAY_ON" : "AUTO_REPAY_OFF",
                    "Auto repayment " + (enabled ? "enabled" : "disabled") + " for " + accNo, accNo);

            return true;
        } catch (Exception e) {
            System.err.println("⚠️ setAutoRepayment failed: " + e.getMessage());
            return false;
        }
    }

    // ======================================================================
    // Audit helper
    // ======================================================================
    private void addAudit(String event, String desc, String actor) {
        try {
            jdbcTemplate.update("""
                INSERT INTO audit_log(event_type, description, actor, timestamp)
                VALUES (?, ?, ?, NOW())
            """, event, desc, actor);
        } catch (Exception ignored) {}
    }

    // ======================================================================
    // Backward-compatible wrapper methods required by other services:
    // markLoanTaken, sendLoanApprovedNotification, makeLoanRepayment, etc.
    // These keep older callers working.
    // ======================================================================

    public boolean markLoanTaken(LoanRequest req) {
        // delegate to approveLoanRequest + applyLoanToAccount
        boolean ok1 = approveLoanRequest(req.getId(), "ADMIN", req.getAdminComment() == null ? "" : req.getAdminComment());
        if (!ok1) return false;
        boolean ok2 = applyLoanToAccount(req);
        if (!ok2) {
            System.err.println("⚠ markLoanTaken: applyLoanToAccount failed for req=" + req.getId());
            return false;
        }
        return true;
    }

    public void sendLoanApprovedNotification(LoanRequest req) {
        try {
            addAudit("LOAN_APPROVED_NOTIFY", "Notify user about approved loan id=" + req.getId() + " acc=" + req.getAccountNumber(), "SYSTEM");
            // hook for real mail/sms integration
        } catch (Exception ignored) {}
    }

    public boolean makeLoanRepayment(String accNo, BigDecimal amount) {
        try {
            jdbcTemplate.update("""
                UPDATE accounts
                SET loan_total_due = loan_total_due - ?,
                    loan_last_paid = NOW()
                WHERE account_number = ?
            """, amount, accNo);
            addAudit("LOAN_REPAY", "Repayment " + amount + " for " + accNo, accNo);
            return true;
        } catch (Exception e) {
            System.err.println("⚠ makeLoanRepayment error: " + e.getMessage());
            return false;
        }
    }

    public Map<String,Object> getLoanTypeAndPlan(String accNo) {
        try {
            return jdbcTemplate.queryForMap("""
            SELECT
                (SELECT loan_type FROM loan_requests 
                 WHERE account_number=? AND status='APPROVED' 
                 ORDER BY id DESC LIMIT 1) AS loanType,
                (SELECT emi_plan FROM loan_requests 
                 WHERE account_number=? AND status='APPROVED' 
                 ORDER BY id DESC LIMIT 1) AS emiPlan
        """, accNo, accNo);
        } catch (Exception e) {
            return Map.of();
        }
    }

}
