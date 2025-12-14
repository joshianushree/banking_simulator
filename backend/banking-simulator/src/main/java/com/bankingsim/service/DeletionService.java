package com.bankingsim.service;

import com.bankingsim.dao.AccountDao;
import com.bankingsim.dao.DeletionRequestDao;
import com.bankingsim.model.Account;
import com.bankingsim.model.DeletionRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeletionService {

    private final DeletionRequestDao deletionRequestDao;
    private final AccountDao accountDao;
    private final OtpService otpService;

    public DeletionService(DeletionRequestDao deletionRequestDao,
                           AccountDao accountDao,
                           OtpService otpService) {
        this.deletionRequestDao = deletionRequestDao;
        this.accountDao = accountDao;
        this.otpService = otpService;
    }

    // ============================================================
    // CUSTOMER — SUBMIT REQUEST
    // ============================================================
    public boolean submitDeletionRequest(DeletionRequest req) {

        // 1) Validate account exists
        Account acc = accountDao.findByAccountNumber(req.getAccountNumber());
        if (acc == null) {
            System.out.println("❌ Cannot create deletion request — account not found.");
            return false;
        }

        // 2) Prevent duplicate deletion request
        if (acc.getDeletionReq() == 1) {
            System.out.println("⚠️ Deletion request already exists for: " + acc.getAccountNumber());
            return false;
        }

        // 3) Save only basic loan status (your model does NOT include details now)
        req.setHasLoan(acc.getTakenLoan() == 1);

        // 4) Save deletion request
        boolean saved = deletionRequestDao.createRequest(req);
        if (!saved) {
            System.out.println("❌ Failed to save deletion request in DB");
            return false;
        }

        // 5) Mark deletion_req = 1 in accounts + users
        accountDao.markDeletionRequested(req.getAccountNumber());

        // 6) Notify user
        sendCustomerRequestReceivedEmail(acc, req);

        return true;
    }

    // ============================================================
    // ADMIN — PENDING REQUESTS
    // ============================================================
    public List<DeletionRequest> getPendingRequests() {
        return deletionRequestDao.getPendingRequests();
    }

    // ============================================================
    // ADMIN — GET SPECIFIC REQUEST
    // ============================================================
    public DeletionRequest getRequestById(long id) {
        return deletionRequestDao.getById(id);
    }

    // ============================================================
    // ADMIN — APPROVE REQUEST
    // ============================================================
    public boolean approveRequest(long reqId, String adminUsername, String comment) {

        DeletionRequest req = deletionRequestDao.getById(reqId);
        if (req == null) return false;

        String accNo = req.getAccountNumber();
        Account acc = accountDao.findByAccountNumber(accNo);
        if (acc == null) return false;

        // ONLY condition: customer cannot delete if loan exists
        if (acc.getTakenLoan() == 1) {
            System.out.println("⚠️ Cannot approve deletion — loan exists for account " + accNo);
            return false;
        }

        // Approve request in DB
        deletionRequestDao.approve(reqId, adminUsername, comment);

        // Mark account deleted
        accountDao.approveDeletion(accNo, adminUsername);

        // Send approval email
        sendCustomerApprovedEmail(acc, req, comment);

        return true;
    }

    // ============================================================
    // ADMIN — REJECT REQUEST
    // ============================================================
    public boolean rejectRequest(long reqId, String adminUsername, String comment) {

        DeletionRequest req = deletionRequestDao.getById(reqId);
        if (req == null) {
            System.out.println("⚠️ Request not found for ID: " + reqId);
            return false;
        }

        String accNo = req.getAccountNumber();
        Account acc = accountDao.findByAccountNumber(accNo);

        // Reject request in DB
        deletionRequestDao.reject(reqId, adminUsername, comment);

        // Reset deletion_req = 0 in accounts + users
        accountDao.rejectDeletion(accNo, adminUsername, comment);

        // Send rejection email
        sendCustomerRejectedEmail(acc, req, comment);

        return true;
    }

    // ============================================================
    // EMAIL NOTIFICATIONS
    // ============================================================
    private void sendCustomerRequestReceivedEmail(Account account, DeletionRequest req) {
        if (otpService == null) return;

        String subject = "Account Deletion Request Received";
        String msg = """
                Hello %s,

                We have received your account deletion request for account: %s.

                Reason Provided:
                %s

                Our admin team will review your request within 48 hours.

                Thank you,
                AstroNova Bank
                """.formatted(
                account.getHolderName(),
                account.getAccountNumber(),
                req.getReason()
        );

        otpService.sendEmail(account.getEmail(), subject, msg);
    }

    private void sendCustomerApprovedEmail(Account account, DeletionRequest req, String comment) {
        if (otpService == null) return;

        String subject = "Your Account Deletion Request Has Been Approved";
        String msg = """
                Hello %s,

                Your account deletion request (%s) has been approved.

                Admin Comment:
                %s

                Your account has now been successfully closed.

                Thank you,
                AstroNova Bank
                """.formatted(
                account.getHolderName(),
                account.getAccountNumber(),
                comment
        );

        otpService.sendEmail(account.getEmail(), subject, msg);
    }

    private void sendCustomerRejectedEmail(Account account, DeletionRequest req, String comment) {
        if (otpService == null) return;

        String subject = "Your Account Deletion Request Has Been Rejected";
        String msg = """
                Hello %s,

                Your request to delete account %s has been rejected.

                Admin Comment:
                %s

                You may update your details and submit a new request anytime.

                Thank you,
                AstroNova Bank
                """.formatted(
                account.getHolderName(),
                account.getAccountNumber(),
                comment
        );

        otpService.sendEmail(account.getEmail(), subject, msg);
    }
}
