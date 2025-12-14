package com.bankingsim.controller;

import com.bankingsim.dao.LoanDao;
import com.bankingsim.dao.TransactionDao;
import com.bankingsim.dao.UserDao;
import com.bankingsim.model.Account;
import com.bankingsim.model.LoanRequest;
import com.bankingsim.service.AccountManager;
import org.antlr.v4.runtime.misc.MurmurHash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.bankingsim.service.OtpService;


@RestController
@RequestMapping("/api/loan")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class LoanController {

    @Autowired private LoanDao loanDao;
    @Autowired private AccountManager accountManager;
    @Autowired private TransactionDao txDao;
    @Autowired private UserDao userDao;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OtpService otpService;


    private String safeError(Throwable e) {
        return (e.getMessage() == null || e.getMessage().isBlank())
                ? "Unexpected server error." : e.getMessage();
    }

    // ============================================================
    // 1️⃣ CUSTOMER SUBMITS LOAN REQUEST
    // ============================================================
    @PostMapping(value = "/request", consumes = "multipart/form-data")
    public Map<String, Object> requestLoan(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) BigDecimal interestRate,
            @RequestParam String loanType,
            @RequestParam String emiPlan,
            @RequestParam String govtIdNumber,
            @RequestParam("govtIdProof") MultipartFile govtIdProofFile,
            @RequestParam String transactionPin,
            HttpSession session) {

        try {
            String sessionAcc = String.valueOf(session.getAttribute("accountNumber"));
            if (!accountNumber.equals(sessionAcc))
                return Map.of("success", false, "message", "Unauthorized loan request.");

            Account acc = accountManager.findAccountByNumber(accountNumber);
            if (acc == null)
                return Map.of("success", false, "message", "Account not found.");

            if ("DELETED".equalsIgnoreCase(acc.getStatus()))
                return Map.of("success", false, "message", "Cannot request loan for a deleted account.");

            if (!userDao.verifyTransactionPin(accountNumber, transactionPin))
                return Map.of("success", false, "message", "Invalid transaction PIN.");

            if (govtIdNumber == null || govtIdNumber.isBlank())
                return Map.of("success", false, "message", "Govt ID number required.");

            if (!govtIdNumber.equalsIgnoreCase(acc.getGovtIdNumber()))
                return Map.of("success", false, "message",
                        "Government ID number does not match the registered ID.");

            if (govtIdProofFile == null || govtIdProofFile.isEmpty())
                return Map.of("success", false, "message", "Govt ID proof file required.");

            byte[] idProof = govtIdProofFile.getBytes();

            BigDecimal requiredBalance =
                    amount.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

            if (acc.getBalance().compareTo(requiredBalance) < 0)
                return Map.of("success", false, "message",
                        "Maintain at least 25% of requested amount (₹" + requiredBalance + ") in account.");

            LoanRequest req = new LoanRequest();
            req.setAccountNumber(accountNumber);
            req.setRequestedAmount(amount);
            req.setInterestRate(interestRate);
            req.setLoanType(loanType);
            req.setEmiPlan(emiPlan);
            req.setGovtIdNumber(govtIdNumber);
            req.setGovtIdProof(idProof);
            req.setRequestedAt(LocalDateTime.now());
            req.setStatus("PENDING");

            long id = loanDao.saveLoanRequest(req);

            if (id <= 0)
                return Map.of("success", false, "message", "Failed to submit loan request.");

            // 🔔 Send mail: loan request submitted
            otpService.sendLoanRequestSubmittedMail(
                    acc.getEmail(),
                    acc.getHolderName(),
                    acc.getAccountNumber(),
                    amount,
                    loanType,
                    emiPlan
            );


            return Map.of("success", true, "message",
                    "Loan request submitted successfully.", "requestId", id);

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ============================================================
    // 2️⃣ EMI PREVIEW
    // ============================================================
    @PostMapping("/emi-preview")
    public Map<String, Object> previewEmi(@RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String loanType = body.get("loanType").toString();
            String emiPlan = body.get("emiPlan").toString();

            double rate = switch (loanType) {
                case "Home Loan" -> 8.0;
                case "Education Loan" -> 6.5;
                case "Personal Loan" -> 11.0;
                default -> 10.0;
            };

            int months = switch (emiPlan) {
                case "MONTHLY" -> 12;
                case "QUARTERLY" -> 4;
                case "YEARLY" -> 1;
                default -> 12;
            };

            BigDecimal interest = amount.multiply(BigDecimal.valueOf(rate))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal total = amount.add(interest);
            BigDecimal emi = total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

            return Map.of(
                    "success", true,
                    "interestRate", rate,
                    "totalPayable", total,
                    "emiAmount", emi,
                    "months", months
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ============================================================
    // 3️⃣ ADMIN — LIST PENDING REQUESTS
    // ============================================================
    @GetMapping("/admin/requests")
    public Map<String, Object> listRequests(HttpSession session) {
        try {
            if (!"ADMIN".equals(session.getAttribute("role")))
                return Map.of("success", false, "message", "Unauthorized.");

            return Map.of("success", true, "requests",
                    loanDao.getAllPendingRequests());

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ============================================================
    // 4️⃣ ADMIN — VIEW SINGLE REQUEST
    // ============================================================
    @GetMapping("/admin/requests/{reqId}")
    public Map<String, Object> getRequest(@PathVariable long reqId, HttpSession session) {
        try {
            if (!"ADMIN".equals(session.getAttribute("role")))
                return Map.of("success", false, "message", "Unauthorized.");

            var req = loanDao.getLoanRequestById(reqId);
            if (req == null)
                return Map.of("success", false, "message", "Request not found.");

            return Map.of("success", true, "request", req);

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ============================================================
    // 5️⃣ ADMIN REVIEW — Suggestion
    // ============================================================
    @GetMapping("/admin/review/{accNo}")
    public Map<String, Object> reviewLoan(@PathVariable String accNo, HttpSession session) {
        try {
            if (!"ADMIN".equals(session.getAttribute("role")))
                return Map.of("success", false, "message", "Unauthorized.");

            BigDecimal avgBalance = loanDao.getAverageBalance(accNo);
            BigDecimal totalDeposits = loanDao.getTotalDepositsLast6Months(accNo);

            String suggestion = "REJECT";
            if (avgBalance.compareTo(new BigDecimal("5000")) > 0 &&
                    totalDeposits.compareTo(new BigDecimal("20000")) > 0)
                suggestion = "APPROVE";

            return Map.of(
                    "success", true,
                    "averageBalance", avgBalance,
                    "totalDeposits", totalDeposits,
                    "suggestion", suggestion,
                    "transactions", txDao.getTransactionsByAccount(accNo)
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ============================================================
    // 6️⃣ ADMIN — APPROVE LOAN  (UPDATED)
    // ============================================================
    @PostMapping("/admin/approve/{reqId}")
    public Map<String, Object> approveLoan(
            @PathVariable long reqId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {

        System.out.println("🔥 APPROVE endpoint hit for ID = " + reqId);

        try {
            if (!"ADMIN".equals(session.getAttribute("role")))
                return Map.of("success", false, "message", "Unauthorized.");

            String adminUser = String.valueOf(session.getAttribute("username"));
            String comment = (String) body.getOrDefault("comment", "");

            LoanRequest req = loanDao.getLoanRequestById(reqId);
            if (req == null)
                return Map.of("success", false, "message", "Request not found.");

            // 1️⃣ MARK REQUEST APPROVED
            loanDao.approveLoanRequest(reqId, adminUser, comment);

            // 2️⃣ CREDIT BALANCE
            Account acc = accountManager.findAccountByNumber(req.getAccountNumber());
            if (acc == null)
                return Map.of("success", false, "message", "Account not found.");

            acc.setBalance(acc.getBalance().add(req.getRequestedAmount()));
            accountManager.updateCustomerDetails(acc);

            System.out.println("💰 Balance credited: +" + req.getRequestedAmount());

            // ⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐
            // ⭐ ADD LOAN CREDIT TRANSACTION HERE ⭐
            // ⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐

            String txId = "TXN-" +
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                    "-" + (int)(Math.random() * 1_0000_0000);

            jdbcTemplate.update("""
                INSERT INTO transactions
                (tx_id, tx_type, to_account, amount, category, status, created_at)
                VALUES (?, 'LOAN_CREDIT', ?, ?, 'Loan Sanctioned', 'SUCCESS', NOW())
            """, txId, req.getAccountNumber(), req.getRequestedAmount());


            System.out.println("📌 Transaction added: LOAN_CREDIT for acc " + req.getAccountNumber());

            // 3️⃣ APPLY LOAN METADATA (taken_loan, interest, total_due etc.)
            loanDao.applyLoanToAccount(req);
            // 🔔 Send mail: loan approved
            otpService.sendLoanApprovedMail(
                    acc.getEmail(),
                    acc.getHolderName(),
                    acc.getAccountNumber(),
                    req.getRequestedAmount(),
                    req.getLoanType(),
                    req.getEmiPlan()
            );

            return Map.of("success", true, "message", "Loan approved, credited & recorded.");


        } catch (Exception e) {
            return Map.of("success", false, "message", "Error: " + e.getMessage());
        }
    }

    // ============================================================
    // 7️⃣ ADMIN — REJECT LOAN
    // ============================================================
    @PostMapping("/admin/reject/{reqId}")
    public Map<String, Object> rejectLoan(
            @PathVariable long reqId,
            @RequestBody Map<String, String> body,
            HttpSession session) {

        try {
            if (!"ADMIN".equals(session.getAttribute("role")))
                return Map.of("success", false, "message", "Unauthorized.");

            String adminUser = String.valueOf(session.getAttribute("username"));
            String comment = body.getOrDefault("comment", "No comment provided");

            boolean ok = loanDao.rejectLoan(reqId, adminUser, comment);
            if (!ok)
                return Map.of("success", false, "message",
                        "Request not found or failed.");

            // ⭐ Fetch request + account AFTER rejecting
            LoanRequest req = loanDao.getLoanRequestById(reqId);
            if (req != null) {
                Account acc = accountManager.findAccountByNumber(req.getAccountNumber());
                if (acc != null) {

                    // ⭐ Send rejection email
                    otpService.sendLoanRejectedMail(
                            acc.getEmail(),
                            acc.getHolderName(),
                            acc.getAccountNumber(),
                            req.getRequestedAmount(),
                            req.getLoanType(),
                            req.getEmiPlan(),
                            comment
                    );
                }
            }

            return Map.of("success", true, "message", "Loan rejected.");

        } catch (Exception e) {
            return Map.of("success", false, "message",
                    "Error: " + e.getMessage());
        }
    }

    // ============================================================
    // 8️⃣ CUSTOMER — EARLY CLOSE LOAN
    // ============================================================
    @PostMapping("/early-close")
    public Map<String, Object> earlyClose(@RequestBody Map<String, Object> body,
                                          HttpSession session) {
        try {
            String accNo = body.get("accountNumber").toString();
            String pin = body.get("transactionPin").toString();

            if (!accNo.equals(String.valueOf(session.getAttribute("accountNumber"))))
                return Map.of("success", false, "message", "Unauthorized.");

            if (!userDao.verifyTransactionPin(accNo, pin))
                return Map.of("success", false, "message", "Invalid transaction PIN.");

            BigDecimal payable = loanDao.getOutstandingAmount(accNo);
            Account acc = accountManager.findAccountByNumber(accNo);

            if (acc.getBalance().compareTo(payable) < 0)
                return Map.of("success", false, "message", "Insufficient balance.");

            // 1️⃣ Deduct balance
            acc.setBalance(acc.getBalance().subtract(payable));
            accountManager.updateBalanceAndActivity(acc);

            // ❌ REMOVE duplicate transaction insert
            // txDao.saveTransactionRepayment(accNo, payable);

            // 2️⃣ Clear loan fields & update loan_last_paid
            loanDao.closeLoan(accNo);

            Account updated = accountManager.findAccountByNumber(accNo);

            // 🔔 Send mail: loan closed / early repayment
            otpService.sendLoanClosedMail(
                    acc.getEmail(),
                    acc.getHolderName(),
                    accNo,
                    payable,
                    updated.getBalance()
            );


            // 3️⃣ INSERT ONLY ONE repayment transaction
            String txId = "TXN-" +
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                    "-" + (int)(Math.random() * 1_0000_0000);

            jdbcTemplate.update("""
            INSERT INTO transactions
            (tx_id, tx_type, from_account, amount, category, status, created_at)
            VALUES (?, 'LOAN_REPAYMENT', ?, ?, 'Loan Early Closure', 'SUCCESS', NOW())
            """, txId, accNo, payable);

            return Map.of(
                    "success", true,
                    "message", "Loan closed successfully.",
                    "newBalance", updated.getBalance()
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }


    // ============================================================
    // 9️⃣ AUTO-REPAY TOGGLE
    // ============================================================
    @PostMapping("/auto-repayment")
    public Map<String, Object> autoRepayment(@RequestBody Map<String, Object> body,
                                             HttpSession session) {
        try {
            String accNo = body.get("accountNumber").toString();
            String pin = body.get("transactionPin").toString();
            boolean enabled = Boolean.parseBoolean(body.get("enabled").toString());

            if (!accNo.equals(String.valueOf(session.getAttribute("accountNumber"))))
                return Map.of("success", false, "message", "Unauthorized.");

            if (!userDao.verifyTransactionPin(accNo, pin))
                return Map.of("success", false, "message", "Invalid transaction PIN.");

            loanDao.setAutoRepayment(accNo, enabled);

            return Map.of("success", true,
                    "message", enabled ? "Auto repayment enabled."
                            : "Auto repayment disabled.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ============================================================
    // 🔟 LOAN STATUS (Customer Dashboard)
    // ============================================================
    @GetMapping("/status/{accountNumber}")
    public Map<String, Object> getLoanStatus(@PathVariable String accountNumber, HttpSession session) {
        try {
            String sessionAcc = String.valueOf(session.getAttribute("accountNumber"));

            if (!accountNumber.equals(sessionAcc)) {
                return Map.of("success", false, "message", "Unauthorized.");
            }

            Account acc = accountManager.findAccountByNumber(accountNumber);
            if (acc == null) {
                return Map.of("success", false, "message", "Account not found.");
            }

            // Use HashMap so null values are allowed
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);

            // taken_loan is usually int 0/1 in DB
            try {
                resp.put("takenLoan", acc.getTakenLoan() == 1);
            } catch (Exception ex) {
                resp.put("takenLoan", false);
            }

            // Null-safe numeric fields
            resp.put("loanAmount",
                    acc.getLoanAmount() != null ? acc.getLoanAmount() : BigDecimal.ZERO);

            resp.put("loanInterestRate",
                    acc.getLoanInterestRate() != null ? acc.getLoanInterestRate() : BigDecimal.ZERO);

            resp.put("loanTotalDue",
                    acc.getLoanTotalDue() != null ? acc.getLoanTotalDue() : BigDecimal.ZERO);

            // auto_repayment_enabled may be Boolean or Integer depending on mapping
            try {
                Object auto = acc.getAutoRepaymentEnabled();
                if (auto instanceof Boolean b) {
                    resp.put("autoRepaymentEnabled", b);
                } else if (auto instanceof Number n) {
                    resp.put("autoRepaymentEnabled", n.intValue() == 1);
                } else {
                    resp.put("autoRepaymentEnabled", false);
                }
            } catch (Exception ex) {
                resp.put("autoRepaymentEnabled", false);
            }

            // This can safely be null in a HashMap
            resp.put("loanLastPaid", acc.getLoanLastPaid());
            // Get loan type + emi plan of the last APPROVED request
            Map<String,Object> meta = loanDao.getLoanTypeAndPlan(accountNumber);
            resp.put("loanType", meta.getOrDefault("loanType", null));
            resp.put("emiPlan", meta.getOrDefault("emiPlan", null));
            return resp;


        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Server error: " + e.getMessage());
        }
    }

}
