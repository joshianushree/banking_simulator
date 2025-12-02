package com.bankingsim.controller;

import com.bankingsim.dao.TransactionDao;
import com.bankingsim.dao.UserDao;
import com.bankingsim.model.TransactionRecord;
import com.bankingsim.service.AccountManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import com.bankingsim.util.PdfGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TransactionController with a lightweight duplicate-request guard.
 * This prevents accidental duplicate HTTP calls from causing double transactions.
 */
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired private AccountManager accountManager;
    @Autowired private TransactionDao transactionDao;
    @Autowired private UserDao userDao; // added for transaction PIN verification

    /** Simple in-memory dedupe cache: key -> timestamp (epoch ms) */
    private static final ConcurrentHashMap<String, Long> recentRequests = new ConcurrentHashMap<>();
    private static final long DEDUPE_WINDOW_MS = 3000L; // 3 seconds

    private boolean isDuplicate(String key) {
        if (key == null) return false;
        long now = Instant.now().toEpochMilli();
        Long prev = recentRequests.get(key);
        if (prev != null && (now - prev) < DEDUPE_WINDOW_MS) {
            return true;
        }
        recentRequests.put(key, now);
        // cleanup occasionally: (not very strict, small map expected)
        return false;
    }

    // -----------------------------------------------------------
    @PostMapping("/deposit")
    public Map<String, Object> deposit(@RequestBody Map<String, Object> body) {
        try {
            String accNo = body.get("accountNumber").toString();
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String category = body.get("category").toString();
            String clientRequestId = body.containsKey("clientRequestId") ? String.valueOf(body.get("clientRequestId")) : null;

            String key = "DEPOSIT|" + accNo + "|" + amount.toPlainString() + "|" + (clientRequestId == null ? "" : clientRequestId);
            if (isDuplicate(key)) {
                // Log and return success to client (idempotent behaviour)
                System.out.println("⚠️ Duplicate deposit request ignored: " + key);
                return Map.of("success", false, "message", "Duplicate request ignored.");
            }

            accountManager.deposit(accNo, amount, category);
            return Map.of("success", true, "message", "Deposit successful.");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    // -----------------------------------------------------------
    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody Map<String, Object> body) {
        try {
            String accNo = body.get("accountNumber").toString();
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String category = body.get("category").toString();
            String clientRequestId = body.containsKey("clientRequestId") ? String.valueOf(body.get("clientRequestId")) : null;

            // NEW: transactionPin required
            if (!body.containsKey("transactionPin") || body.get("transactionPin") == null) {
                return Map.of("success", false, "message", "transactionPin is required.");
            }
            String transactionPin = body.get("transactionPin").toString();

            String key = "WITHDRAW|" + accNo + "|" + amount.toPlainString() + "|" + (clientRequestId == null ? "" : clientRequestId);
            if (isDuplicate(key)) {
                System.out.println("⚠️ Duplicate withdraw request ignored: " + key);
                return Map.of("success", false, "message", "Duplicate request ignored.");
            }

            // NEW: verify transaction PIN before proceeding
            boolean pinOk = userDao.verifyTransactionPin(accNo, transactionPin);
            if (!pinOk) {
                System.out.println("🚫 Withdraw blocked: invalid transaction PIN for account " + accNo);
                return Map.of("success", false, "message", "Transaction PIN is incorrect.");
            }

            accountManager.withdraw(accNo, amount, category);
            return Map.of("success", true, "message", "Withdrawal successful.");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    // -----------------------------------------------------------
    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestBody Map<String, Object> body) {
        try {
            String from = body.get("fromAccount").toString();
            String to = body.get("toAccount").toString();
            String ifsc = body.get("ifsc").toString();
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String category = body.get("category").toString();
            String clientRequestId = body.containsKey("clientRequestId") ? String.valueOf(body.get("clientRequestId")) : null;

            // NEW: transactionPin required
            if (!body.containsKey("transactionPin") || body.get("transactionPin") == null) {
                return Map.of("success", false, "message", "transactionPin is required.");
            }
            String transactionPin = body.get("transactionPin").toString();

            if (from.equals(to)) {
                return Map.of("success", false, "message", "Cannot transfer to the same account.");
            }

            String key = "TRANSFER|" + from + "|" + to + "|" + amount.toPlainString() + "|" + (clientRequestId == null ? "" : clientRequestId);
            if (isDuplicate(key)) {
                System.out.println("⚠️ Duplicate transfer request ignored: " + key);
                return Map.of("success", false, "message", "Duplicate request ignored.");
            }

            // NEW: verify transaction PIN for the 'from' account before proceeding
            boolean pinOk = userDao.verifyTransactionPin(from, transactionPin);
            if (!pinOk) {
                System.out.println("🚫 Transfer blocked: invalid transaction PIN for account " + from);
                return Map.of("success", false, "message", "Transaction PIN is incorrect.");
            }

            accountManager.transfer(from, to, amount, category, ifsc);
            return Map.of("success", true, "message", "Transfer successful.");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    // -----------------------------------------------------------
    @GetMapping
    public List<TransactionRecord> getAll() {
        return transactionDao.getAllTransactions();
    }

    @GetMapping("/{accNo}")
    public List<TransactionRecord> getTransactionsByAccount(@PathVariable String accNo) {
        return transactionDao.getTransactionsByAccount(accNo);
    }

    /**
     * New: filtered transactions endpoint.
     *
     * Query params supported (all optional):
     * - fromDate (ISO date or ISO date-time) e.g. 2025-11-01 or 2025-11-01T00:00:00
     * - toDate   (ISO date or ISO date-time)
     * - type     (DEPOSIT / WITHDRAW / TRANSFER / ROLLBACK / ACCOUNT_CLOSED)
     * - category (string)
     * - status   (SUCCESS / REVERSED / ...)
     * - account  (account number to filter from OR to)
     * - limit    (int)
     * - offset   (int)
     */
    @GetMapping("/filter")
    public List<TransactionRecord> getFilteredTransactions(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String account,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset
    ) {

        LocalDateTime from = parseToStartOfDay(fromDate);
        LocalDateTime to = parseToEndOfDay(toDate);

        return transactionDao.getTransactionsWithFilters(from, to, type, category, status, account, limit, offset);
    }

    private LocalDateTime parseToStartOfDay(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            if (s.contains("T")) {
                return LocalDateTime.parse(s);
            } else {
                LocalDate d = LocalDate.parse(s);
                return d.atStartOfDay();
            }
        } catch (DateTimeParseException e) {
            System.err.println("⚠️ Failed to parse date (start): " + s + " -> " + e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseToEndOfDay(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            if (s.contains("T")) {
                return LocalDateTime.parse(s);
            } else {
                LocalDate d = LocalDate.parse(s);
                return d.atTime(23, 59, 59);
            }
        } catch (DateTimeParseException e) {
            System.err.println("⚠️ Failed to parse date (end): " + s + " -> " + e.getMessage());
            return null;
        }
    }

    // consolidated rollback kept as-is
    @PatchMapping("/rollback/{txId}")
    public Map<String, Object> rollbackTx(@PathVariable String txId) {
        boolean ok = transactionDao.rollbackTransaction(txId, "ADMIN");
        return ok ? Map.of("success", true, "message", "Transaction rolled back.") : Map.of("success", false, "message", "Rollback failed or already reversed.");
    }

    @CrossOrigin(
            origins = "http://localhost:3000",
            exposedHeaders = {"Content-Disposition"}   // IMPORTANT
    )
    @GetMapping("/ministatement/{accNo}")
    public ResponseEntity<Resource> downloadMiniStatement(@PathVariable String accNo) {

        try {
            // generate PDF
            List<TransactionRecord> tx = transactionDao.getTransactionsByAccount(accNo);
            PdfGenerator.generateMiniStatementPdf(accNo, tx);

            File file = new File("reports/MiniStatement_" + accNo + ".pdf");
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=MiniStatement_" + accNo + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(file.length())
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
