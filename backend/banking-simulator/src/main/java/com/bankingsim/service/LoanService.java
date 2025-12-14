package com.bankingsim.service;

import com.bankingsim.dao.AccountDao;
import com.bankingsim.dao.LoanDao;
import com.bankingsim.dao.TransactionDao;
import com.bankingsim.model.Account;
import com.bankingsim.model.LoanRequest;
import com.bankingsim.model.TransactionRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class LoanService {

    private final LoanDao loanDao;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;

    public LoanService(LoanDao loanDao, AccountDao accountDao, TransactionDao transactionDao) {
        this.loanDao = loanDao;
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
    }

    // -------------------------------------------------------
    // CUSTOMER — Submit a new loan request
    // -------------------------------------------------------
    public long requestLoan(LoanRequest req) {
        Account acc = accountDao.findByAccountNumber(req.getAccountNumber());

        if (acc == null || "DELETED".equalsIgnoreCase(acc.getStatus())) {
            return -1L;
        }

        return loanDao.saveLoanRequest(req);
    }

    // -------------------------------------------------------
    // ADMIN — Get all pending requests
    // -------------------------------------------------------
    public List<Map<String, Object>> getPendingRequests() {
        return loanDao.getAllPendingRequests();
    }

    // -------------------------------------------------------
    // ADMIN — Approve loan request
    // -------------------------------------------------------
    public boolean approveRequest(long reqId, String adminComment) {

        LoanRequest req = loanDao.getLoanRequestById(reqId);
        if (req == null) return false;

        // Store admin comment in the object
        req.setAdminComment(adminComment);

        // Fetch account
        Account acc = accountDao.findByAccountNumber(req.getAccountNumber());
        if (acc == null) return false;

        // FIRST: credit loan amount to user's balance
        acc.setBalance(acc.getBalance().add(req.getRequestedAmount()));
        accountDao.updateBalanceAndActivity(acc);

        // SECOND: mark loan approved in loan_requests
        boolean ok = loanDao.markLoanTaken(req);
        if (!ok) return false;

        // THIRD: apply loan fields to accounts table
        boolean applied = loanDao.applyLoanToAccount(req);
        if (!applied) return false;

        // Notify (audit)
        loanDao.sendLoanApprovedNotification(req);

        // Save transaction for loan credit
        TransactionRecord tx = new TransactionRecord(
                TransactionRecord.TxType.DEPOSIT,
                null,
                req.getAccountNumber(),
                req.getRequestedAmount(),
                "Loan Credited"
        );
        tx.setCreatedAt(LocalDateTime.now());
        transactionDao.saveTransaction(tx);

        return true;
    }

    // -------------------------------------------------------
    // ADMIN — Reject loan request
    // -------------------------------------------------------
    public boolean rejectRequest(long reqId, String comment) {
        return loanDao.rejectLoan(reqId, comment, "ADMIN");
    }

    // -------------------------------------------------------
    // CUSTOMER — Repay loan
    // -------------------------------------------------------
    public boolean repay(String accNo, BigDecimal amount) {
        Account acc = accountDao.findByAccountNumber(accNo);
        if (acc == null) return false;

        if (acc.getBalance().compareTo(amount) < 0) return false;

        // Deduct money
        acc.setBalance(acc.getBalance().subtract(amount));
        accountDao.updateBalanceAndActivity(acc);

        // Update loan outstanding
        boolean ok = loanDao.makeLoanRepayment(accNo, amount);

        if (!ok) {
            // rollback
            acc.setBalance(acc.getBalance().add(amount));
            accountDao.updateBalanceAndActivity(acc);
            return false;
        }

        // Save repayment transaction
        TransactionRecord tx = new TransactionRecord(
                TransactionRecord.TxType.LOAN_REPAYMENT,
                accNo,
                null,
                amount,
                "Loan Repayment"
        );
        tx.setCreatedAt(LocalDateTime.now());
        transactionDao.saveTransaction(tx);

        return true;
    }

    // -------------------------------------------------------
    // CUSTOMER — Toggle Auto Repayment
    // -------------------------------------------------------
    public boolean toggleAutoRepayment(String accNo, boolean enabled) {
        return loanDao.setAutoRepayment(accNo, enabled);
    }

    // -------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------
    public BigDecimal getAverageBalance(String accNo) {
        return loanDao.getAverageBalance(accNo);
    }

    public BigDecimal getTotalDepositsLast6Months(String accNo) {
        return loanDao.getTotalDepositsLast6Months(accNo);
    }

    public BigDecimal getOutstandingAmount(String accNo) {
        return loanDao.getOutstandingAmount(accNo);
    }
}
