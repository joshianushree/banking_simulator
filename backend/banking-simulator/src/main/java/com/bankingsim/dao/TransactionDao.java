package com.bankingsim.dao;

import com.bankingsim.model.TransactionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for managing transactions with rollback & audit integration.
 *
 * NOTE: CSV file writes and automatic PDF refresh have been removed.
 * Transactions are stored in DB; PDFs are generated only-on-demand.
 *
 * Extended:
 *  - createAndSaveTransaction(...) boolean wrapper
 *  - hasPendingOutgoingTransactions(account) quick check
 *  - countPendingTransactionsForAccount(account)
 *  - sumUnsettledAmountsForAccount(account)
 *
 * Existing behavior preserved.
 */
@Repository
public class TransactionDao {

    private final JdbcTemplate jdbcTemplate;

    public TransactionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------- CREATE ----------------------
    public void saveTransaction(TransactionRecord tx) {
        try {
            if (tx == null || tx.getAmount() == null || tx.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("⚠️ Invalid transaction request.");
                return;
            }

            // prevent self-transfer
            if (tx.getTxType() == TransactionRecord.TxType.TRANSFER &&
                    tx.getFromAccount() != null &&
                    tx.getFromAccount().equals(tx.getToAccount())) {
                System.out.println("⚠️ Cannot transfer to the same account. Transaction aborted.");
                return;
            }

            // SAFEGUARD: if withdraw/transfer and fromAccount has tx_locked = TRUE, abort
            if ((tx.getTxType() == TransactionRecord.TxType.WITHDRAW || tx.getTxType() == TransactionRecord.TxType.TRANSFER)
                    && tx.getFromAccount() != null) {

                Boolean txLocked = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(tx_locked, FALSE) FROM accounts WHERE account_number = ?",
                        Boolean.class, tx.getFromAccount());

                if (Boolean.TRUE.equals(txLocked)) {
                    System.out.println("🚫 Aborting transaction because transaction functionality is locked for account: " + tx.getFromAccount());
                    addAudit("TX_ABORTED_LOCKED", "Attempted transaction while tx_locked: " + tx, tx.getFromAccount());
                    return;
                }
            }

            String sql = """
                INSERT INTO transactions 
                (tx_id, tx_type, from_account, to_account, amount, category, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', ?)
            """;

            Timestamp createdTs = Timestamp.valueOf(tx.getCreatedAt() != null ? tx.getCreatedAt() : LocalDateTime.now());

            jdbcTemplate.update(sql,
                    tx.getTxId(),
                    tx.getTxType() != null ? tx.getTxType().name() : "UNKNOWN",
                    sanitizeAccount(tx.getFromAccount()),
                    sanitizeAccount(tx.getToAccount()),
                    tx.getAmount(),
                    tx.getCategory() != null ? tx.getCategory() : "General",
                    createdTs
            );

            addAudit("TRANSACTION_" + tx.getTxType(), "Executed transaction: " + tx,
                    tx.getFromAccount() != null ? tx.getFromAccount() : "SYSTEM");

            System.out.println("✅ Transaction saved successfully: " + tx.getTxId());

            // NOTE: no CSV append and no automatic PDF refresh here — on-demand only.

        } catch (Exception e) {
            System.err.println("❌ Failed to save transaction: " + e.getMessage());
        }
    }

    /**
     * Wrapper that tries to save a transaction and returns boolean success.
     * Useful for services that need immediate boolean feedback.
     */
    public boolean createAndSaveTransaction(TransactionRecord tx) {
        try {
            saveTransaction(tx);
            return true;
        } catch (Exception e) {
            System.err.println("⚠️ createAndSaveTransaction failed: " + e.getMessage());
            return false;
        }
    }

    // ---------------------- ROLLBACK ----------------------
    public boolean rollbackTransaction(String txId, String adminUser) {
        try {
            TransactionRecord tx = jdbcTemplate.queryForObject(
                    "SELECT * FROM transactions WHERE tx_id = ?",
                    new TransactionRowMapper(), txId);

            if (tx == null) {
                System.out.println("⚠️ Transaction not found: " + txId);
                return false;
            }

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM transactions WHERE tx_id = ?",
                    String.class, txId);

            if ("REVERSED".equalsIgnoreCase(status)) {
                System.out.println("⚠️ Transaction already reversed: " + txId);
                return false;
            }

            BigDecimal amount = tx.getAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("⚠️ Invalid rollback amount.");
                return false;
            }

            // Reverse balance changes
            switch (tx.getTxType()) {
                case DEPOSIT -> {
                    if (tx.getToAccount() != null)
                        jdbcTemplate.update(
                                "UPDATE accounts SET balance = balance - ?, last_activity=? WHERE account_number=? AND balance >= ?",
                                amount, LocalDateTime.now(), tx.getToAccount(), amount);
                }
                case WITHDRAW -> {
                    if (tx.getFromAccount() != null)
                        jdbcTemplate.update(
                                "UPDATE accounts SET balance = balance + ?, last_activity=? WHERE account_number=?",
                                amount, LocalDateTime.now(), tx.getFromAccount());
                }
                case TRANSFER -> {
                    if (tx.getFromAccount() != null)
                        jdbcTemplate.update(
                                "UPDATE accounts SET balance = balance + ?, last_activity=? WHERE account_number=?",
                                amount, LocalDateTime.now(), tx.getFromAccount());
                    if (tx.getToAccount() != null)
                        jdbcTemplate.update(
                                "UPDATE accounts SET balance = balance - ?, last_activity=? WHERE account_number=? AND balance >= ?",
                                amount, LocalDateTime.now(), tx.getToAccount(), amount);
                }
                default -> System.out.println("⚠️ Unsupported rollback type: " + tx.getTxType());
            }

            jdbcTemplate.update("""
                UPDATE transactions 
                SET status='REVERSED', rolled_back_by=?, created_at=created_at
                WHERE tx_id=?
            """, adminUser, txId);

            addAudit("ROLLBACK", "Transaction rolled back: " + txId, adminUser);
            System.out.println("✅ Rollback successful for transaction: " + txId);

            // NOTE: no automatic PDF refresh here.

            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Rollback failed for " + txId + ": " + e.getMessage());
            return false;
        }
    }

    // ---------------------- READ ----------------------
    public List<TransactionRecord> fetchLastNForAccount(String accNum, int limit) {
        String sql = """
            SELECT * FROM transactions 
            WHERE from_account = ? OR to_account = ? 
            ORDER BY created_at DESC 
            LIMIT ?
        """;
        return jdbcTemplate.query(sql, new TransactionRowMapper(), accNum, accNum, limit);
    }

    public List<TransactionRecord> getTransactionsByAccount(String accNum) {
        String sql = """
            SELECT * FROM transactions 
            WHERE from_account = ? OR to_account = ? 
            ORDER BY created_at DESC
        """;
        return jdbcTemplate.query(sql, new TransactionRowMapper(), accNum, accNum);
    }

    public List<TransactionRecord> getAllTransactions() {
        String sql = "SELECT * FROM transactions ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, new TransactionRowMapper());
    }

    public List<TransactionRecord> getAllTransactionsByBranch(String branch) {
        String sql = """
        SELECT t.*
        FROM transactions t
        JOIN accounts a 
            ON t.from_account = a.account_number 
            OR t.to_account = a.account_number
        WHERE a.branch_name = ?
        ORDER BY t.created_at ASC
    """;

        return jdbcTemplate.query(sql, new TransactionRowMapper(), branch);
    }

    public List<TransactionRecord> getTransactionsWithFilters(
            java.time.LocalDateTime from,
            java.time.LocalDateTime to,
            String type,
            String category,
            String status,
            String account,
            Integer limit,
            Integer offset
    ) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1 ");
            List<Object> params = new ArrayList<>();

            if (from != null) {
                sql.append(" AND created_at >= ? ");
                params.add(Timestamp.valueOf(from));
            }
            if (to != null) {
                sql.append(" AND created_at <= ? ");
                params.add(Timestamp.valueOf(to));
            }
            if (type != null && !type.isBlank()) {
                sql.append(" AND tx_type = ? ");
                params.add(type.trim().toUpperCase());
            }
            if (category != null && !category.isBlank()) {
                sql.append(" AND category ILIKE ? ");
                params.add("%" + category.trim() + "%");
            }
            if (status != null && !status.isBlank()) {
                sql.append(" AND status = ? ");
                params.add(status.trim().toUpperCase());
            }
            if (account != null && !account.isBlank()) {
                sql.append(" AND (from_account = ? OR to_account = ?) ");
                params.add(account.trim());
                params.add(account.trim());
            }

            sql.append(" ORDER BY created_at DESC ");

            int safeLimit = (limit == null || limit <= 0) ? 100 : limit;
            int safeOffset = (offset == null || offset < 0) ? 0 : offset;

            sql.append(" LIMIT ? OFFSET ? ");
            params.add(safeLimit);
            params.add(safeOffset);

            return jdbcTemplate.query(sql.toString(), params.toArray(), new TransactionRowMapper());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to run filtered transactions query: " + e.getMessage());
            return List.of();
        }
    }

    // ---------------------- NEW HELPERS FOR DELETION / LOAN CHECKS ----------------------

    /**
     * Quick check to see if there are any recent outgoing transactions for account that could block deletion.
     * This is a heuristic (not a strict transactional locking mechanism). It looks for outgoing transactions in the last 24 hours
     * that are not reversed.
     *
     * @param accNo account number
     * @return true if there are outgoing transactions recently, false otherwise (or on error)
     */
    public boolean hasPendingOutgoingTransactions(String accNo) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM transactions
                    WHERE from_account = ?
                      AND status <> 'REVERSED'
                      AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
                """, Integer.class, accNo);
            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("⚠️ Error checking pending outgoing transactions: " + e.getMessage());
            return true; // be conservative — if error, prevent deletion
        }
    }

    /**
     * Count pending (non-reversed) transactions for an account overall.
     */
    public int countPendingTransactionsForAccount(String accNo) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM transactions
                    WHERE (from_account = ? OR to_account = ?)
                      AND status <> 'REVERSED'
                """, Integer.class, accNo, accNo);
            return count == null ? 0 : count;
        } catch (Exception e) {
            System.err.println("⚠️ Error counting pending transactions: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Sum of amounts that are not reversed for this account (incoming as credit).
     * Useful to detect large unsettled credits before deletion.
     */
    public BigDecimal sumUnsettledAmountsForAccount(String accNo) {
        try {
            BigDecimal sum = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(amount),0) FROM transactions
                    WHERE to_account = ?
                      AND status <> 'REVERSED'
                """, BigDecimal.class, accNo);
            return sum == null ? BigDecimal.ZERO : sum;
        } catch (Exception e) {
            System.err.println("⚠️ Error summing unsettled amounts: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ---------------------- UTIL ----------------------
    private String sanitizeAccount(String acc) {
        if (acc == null) return null;
        String trimmed = acc.trim();
        return (trimmed.isEmpty() || "-".equals(trimmed)) ? null : trimmed;
    }

    private void addAudit(String event, String desc, String actor) {
        try {
            jdbcTemplate.update("""
                INSERT INTO audit_log (event_type, description, actor, timestamp)
                VALUES (?, ?, ?, NOW())
            """, event, desc, actor);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to add audit log: " + e.getMessage());
        }
    }

    public void saveTransactionRepayment(String accNo, BigDecimal amount) {
        String txId = "TXN-" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                "-" + (int)(Math.random() * 1_0000_0000);

        jdbcTemplate.update("""
        INSERT INTO transactions
        (tx_id, tx_type, from_account, amount, category, status, created_at)
        VALUES (?, 'LOAN_REPAYMENT', ?, ?, 'Loan Early Closure', 'SUCCESS', NOW())
    """, txId, accNo, amount);
    }


    // ---------------------- MAPPER ----------------------
    private static class TransactionRowMapper implements RowMapper<TransactionRecord> {
        @Override
        public TransactionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {

            TransactionRecord.TxType type = null;
            String typeStr = rs.getString("tx_type");
            if (typeStr != null) {
                try {
                    type = TransactionRecord.TxType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("⚠️ Unknown TxType in DB: " + typeStr);
                }
            }

            TransactionRecord tx = new TransactionRecord(
                    type,
                    rs.getString("from_account"),
                    rs.getString("to_account"),
                    rs.getBigDecimal("amount"),
                    rs.getString("category")
            );

            tx.setTxId(rs.getString("tx_id"));

            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) {
                tx.setCreatedAt(ts.toLocalDateTime());
                tx.setTimestamp(ts.toLocalDateTime().toString());
            } else {
                tx.setTimestamp(null);
            }

            String status = rs.getString("status");
            tx.setStatus(status != null ? status : "SUCCESS");

            return tx;
        }
    }

}
