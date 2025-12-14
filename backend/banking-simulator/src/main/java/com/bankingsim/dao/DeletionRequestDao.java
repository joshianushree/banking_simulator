package com.bankingsim.dao;

import com.bankingsim.model.DeletionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeletionRequestDao {

    private final JdbcTemplate jdbcTemplate;

    public DeletionRequestDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===================================================================
    // CREATE REQUEST
    // ===================================================================
    public boolean createRequest(DeletionRequest req) {
        String sql = """
            INSERT INTO deletion_requests
            (account_number, requester_name, requester_email, requester_phone, reason, status)
            VALUES (?, ?, ?, ?, ?, 'PENDING')
        """;

        try {
            int rows = jdbcTemplate.update(
                    sql,
                    req.getAccountNumber(),
                    req.getHolderName(),
                    req.getEmail(),
                    req.getPhone(),
                    req.getReason()
            );
            return rows > 0;
        } catch (Exception e) {
            System.err.println("❌ Failed to insert deletion request: " + e.getMessage());
            return false;
        }
    }

    // ===================================================================
    // GET ALL PENDING REQUESTS — NOW WITH LOAN INFO
    // ===================================================================
    public List<DeletionRequest> getPendingRequests() {

        String sql = """
            SELECT 
                dr.id,
                dr.account_number,
                dr.requester_name,
                dr.requester_email,
                dr.requester_phone,
                dr.reason,
                dr.status,
                dr.admin_comment,

                a.taken_loan,
                a.loan_amount,
                a.loan_total_due,
                a.loan_type,
                a.emi_plan

            FROM deletion_requests dr
            JOIN accounts a ON a.account_number = dr.account_number
            WHERE dr.status = 'PENDING'
            ORDER BY dr.id DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {

            DeletionRequest req = new DeletionRequest();
            req.setId(rs.getLong("id"));
            req.setAccountNumber(rs.getString("account_number"));
            req.setHolderName(rs.getString("requester_name"));
            req.setEmail(rs.getString("requester_email"));
            req.setPhone(rs.getString("requester_phone"));
            req.setReason(rs.getString("reason"));
            req.setStatus(rs.getString("status"));
            req.setAdminComment(rs.getString("admin_comment"));

            // ⭐ LOAN FIELDS — FIX FOR ADMIN PAGE
            req.setHasLoan(rs.getInt("taken_loan") == 1);
            req.setLoanAmount(rs.getBigDecimal("loan_amount") != null ?
                    rs.getBigDecimal("loan_amount").doubleValue() : 0);

            req.setLoanTotalDue(rs.getBigDecimal("loan_total_due") != null ?
                    rs.getBigDecimal("loan_total_due").doubleValue() : 0);

            req.setLoanType(rs.getString("loan_type"));
            req.setEmiPlan(rs.getString("emi_plan"));

            return req;
        });
    }

    // ===================================================================
    // GET REQUEST BY ID — NOW WITH LOAN DETAILS
    // ===================================================================
    public DeletionRequest getById(long id) {

        String sql = """
            SELECT 
                dr.id,
                dr.account_number,
                dr.requester_name,
                dr.requester_email,
                dr.requester_phone,
                dr.reason,
                dr.status,
                dr.admin_comment,

                a.taken_loan,
                a.loan_amount,
                a.loan_total_due,
                a.loan_type,
                a.emi_plan

            FROM deletion_requests dr
            JOIN accounts a ON a.account_number = dr.account_number
            WHERE dr.id = ?
        """;

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {

                DeletionRequest req = new DeletionRequest();
                req.setId(rs.getLong("id"));
                req.setAccountNumber(rs.getString("account_number"));
                req.setHolderName(rs.getString("requester_name"));
                req.setEmail(rs.getString("requester_email"));
                req.setPhone(rs.getString("requester_phone"));
                req.setReason(rs.getString("reason"));
                req.setStatus(rs.getString("status"));
                req.setAdminComment(rs.getString("admin_comment"));

                // ⭐ Loan fields
                req.setHasLoan(rs.getInt("taken_loan") == 1);
                req.setLoanAmount(rs.getBigDecimal("loan_amount") != null ?
                        rs.getBigDecimal("loan_amount").doubleValue() : 0);

                req.setLoanTotalDue(rs.getBigDecimal("loan_total_due") != null ?
                        rs.getBigDecimal("loan_total_due").doubleValue() : 0);

                req.setLoanType(rs.getString("loan_type"));
                req.setEmiPlan(rs.getString("emi_plan"));

                return req;
            }, id);

        } catch (Exception e) {
            return null;
        }
    }

    // ===================================================================
    // APPROVE
    // ===================================================================
    public void approve(long id, String admin, String comment) {
        String sql = """
            UPDATE deletion_requests
            SET status='APPROVED',
                admin_comment=?,
                processed_by=?,
                processed_at=NOW()
            WHERE id=?
        """;

        jdbcTemplate.update(sql, comment, admin, id);
    }

    // ===================================================================
    // REJECT
    // ===================================================================
    public void reject(long id, String admin, String comment) {
        String sql = """
            UPDATE deletion_requests
            SET status='REJECTED',
                admin_comment=?,
                processed_by=?,
                processed_at=NOW()
            WHERE id=?
        """;

        jdbcTemplate.update(sql, comment, admin, id);
    }
}
