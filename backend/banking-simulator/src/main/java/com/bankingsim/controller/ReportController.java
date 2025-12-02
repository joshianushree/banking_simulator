package com.bankingsim.controller;

import com.bankingsim.model.Account;
import com.bankingsim.service.AccountManager;
import com.bankingsim.dao.TransactionDao;
import com.bankingsim.dao.UserDao;
import com.bankingsim.model.User;
import com.bankingsim.util.PdfGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.*;


@CrossOrigin(
        origins = "http://localhost:3000",
        exposedHeaders = {"Content-Disposition", "X-Error-Message"}
)
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private AccountManager manager;

    @Autowired
    private TransactionDao txDao;

    @Autowired
    private UserDao userDao;


    // ------------------------------------------------------
    // ACCOUNTS REPORT
    // ------------------------------------------------------
    @GetMapping("/accounts")
    public ResponseEntity<Resource> downloadAccounts(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "branch", required = false) String branch) {

        manager.generateAccountsPdf(); // legacy no-op

        String key = null;
        if (username != null && !username.isBlank()) {
            User u = userDao.findByUsername(username.trim());
            if (u != null) key = buildKeyFromUser(u);
        }

        // FETCH FILTERED ACCOUNTS
        var accounts = (branch == null || branch.isBlank())
                ? manager.listAllAccounts()
                : manager.listAccountsByBranch(branch.trim());

        byte[] pdf = PdfGenerator.generateAccountsPdfBytes(accounts, key);

        return buildPdfResponse(pdf, "Accounts_Report.pdf");
    }


    // ------------------------------------------------------
    // TRANSACTIONS REPORT
    // ------------------------------------------------------
    @GetMapping("/transactions")
    public ResponseEntity<Resource> downloadTransactions(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "branch", required = false) String branch) {

        manager.generateTransactionsPdf(); // legacy no-op

        String key = null;
        if (username != null && !username.isBlank()) {
            User u = userDao.findByUsername(username.trim());
            if (u != null) key = buildKeyFromUser(u);
        }

        // FETCH FILTERED TRANSACTIONS
        var tx = (branch == null || branch.isBlank())
                ? txDao.getAllTransactions()
                : txDao.getAllTransactionsByBranch(branch.trim());

        byte[] pdf = PdfGenerator.generateTransactionsPdfBytes(tx, key);

        return buildPdfResponse(pdf, "Transactions_Report.pdf");
    }



    // ------------------------------------------------------
    // MINI STATEMENT
    // ------------------------------------------------------
    @GetMapping("/ministatement/{accNo}")
    public ResponseEntity<Resource> downloadMini(@PathVariable String accNo) {

        if (accNo == null || accNo.trim().isEmpty())
            return ResponseEntity.badRequest().build();

        if (!manager.accountExists(accNo))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        manager.generateMiniStatementPdf(accNo);  // legacy no-op

        User u = userDao.findByAccountNumber(accNo);
        String key = (u != null ? buildKeyFromUser(u) : null);

        // Use the 3-arg PdfGenerator API (accNo, transactions, key)
        byte[] pdf = PdfGenerator.generateMiniStatementPdfBytes(
                accNo,
                txDao.getTransactionsByAccount(accNo),
                key
        );

        return buildPdfResponse(pdf, "MiniStatement_" + accNo + ".pdf");
    }


    // ------------------------------------------------------
    // COMMON RESPONSE BUILDER
    // ------------------------------------------------------
    private ResponseEntity<Resource> buildPdfResponse(byte[] pdf, String filename) {
        ByteArrayResource res = new ByteArrayResource(pdf);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(res);
    }


    // ------------------------------------------------------
    // KEY BUILDER
    // ------------------------------------------------------
    private String buildKeyFromUser(User u) {
        try {
            String username = u.getUsername();
            String phone = u.getPhone();

            String first3 = username.substring(0, Math.min(3, username.length()));
            String last4 = phone.substring(Math.max(0, phone.length() - 4));

            return first3 + last4;
        } catch (Exception e) {
            return null;
        }
    }
}
