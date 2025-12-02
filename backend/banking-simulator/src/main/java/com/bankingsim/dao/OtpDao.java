package com.bankingsim.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public class OtpDao {

    private final JdbcTemplate jdbcTemplate;

    public OtpDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveOtp(String userIdentifier, String otp, LocalDateTime expiresAt) {
        String sql = """
            INSERT INTO otp_verification (user_identifier, otp_code, expires_at, verified)
            VALUES (?, ?, ?, FALSE)
        """;
        jdbcTemplate.update(sql, userIdentifier, otp, expiresAt);
    }

    public String getLatestOtp(String userIdentifier) {
        String sql = """
            SELECT otp_code FROM otp_verification
            WHERE user_identifier = ? AND verified = FALSE
            ORDER BY created_at DESC LIMIT 1
        """;
        try {
            return jdbcTemplate.queryForObject(sql, String.class, userIdentifier);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean verifyOtp(String userIdentifier, String otp) {
        String sql = """
            SELECT COUNT(*) FROM otp_verification
            WHERE user_identifier = ? AND otp_code = ? 
            AND verified = FALSE AND expires_at > NOW()
        """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userIdentifier, otp);
        if (count != null && count > 0) {
            markAsVerified(userIdentifier, otp);
            return true;
        }
        return false;
    }

    private void markAsVerified(String userIdentifier, String otp) {
        String sql = """
            UPDATE otp_verification SET verified = TRUE 
            WHERE user_identifier = ? AND otp_code = ?
        """;
        jdbcTemplate.update(sql, userIdentifier, otp);
    }

    public void deleteExpiredOtps() {
        String sql = "DELETE FROM otp_verification WHERE expires_at < NOW()";
        jdbcTemplate.execute(sql);
    }
}
