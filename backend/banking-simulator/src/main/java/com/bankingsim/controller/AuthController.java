package com.bankingsim.controller;

import com.bankingsim.dao.AccountDao;
import com.bankingsim.dao.UserDao;
import com.bankingsim.model.Account;
import com.bankingsim.model.User;
import com.bankingsim.service.OtpService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserDao userDao;
    @Autowired private AccountDao accountDao;
    @Autowired private OtpService otpService;

    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom secureRandom = new SecureRandom();

    private String safeError(Throwable e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank())
                ? "Unexpected server error occurred."
                : msg;
    }

    // ------------------------------
    // ADMIN LOGIN
    // ------------------------------
    @PostMapping("/admin/login")
    public Map<String, Object> adminLogin(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || username.isBlank() ||
                    password == null || password.isBlank()) {
                return Map.of("success", false, "message", "Username and password are required.");
            }

            if (!userDao.usernameExists(username))
                return Map.of("success", false, "message", "Admin not found.");

            if (userDao.isUserLocked(username))
                return Map.of("success", false, "message", "Account locked.");

            boolean valid = userDao.validateAdmin(username, password);
            if (!valid)
                return Map.of("success", false, "message", "Invalid password.");

            User admin = userDao.findByUsername(username);

            String otp = otpService.generateOtp();
            otpService.sendOtp(username, admin.getEmail(), admin.getPhone(), otp);

            return Map.of(
                    "success", true,
                    "identifier", username,
                    "message", "OTP sent to registered email and mobile."
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ------------------------------
    // CUSTOMER LOGIN
    // ------------------------------
    @PostMapping("/customer/login")
    public Map<String, Object> customerLogin(@RequestBody Map<String, String> body) {
        try {
            String accNo = body.get("accountNumber");
            String pin = body.get("pin");

            if (accNo == null || accNo.isBlank() ||
                    pin == null || pin.isBlank()) {
                return Map.of("success", false, "message", "Account number and PIN are required.");
            }

            if (!userDao.accountExists(accNo))
                return Map.of("success", false, "message", "Account not found.");

            if (userDao.isAccountLocked(accNo))
                return Map.of("success", false, "message", "Account locked.");

            User user = userDao.findByAccountNumber(accNo);
            if (user == null)
                return Map.of("success", false, "message", "User not found.");

            // ❌ BLOCK DELETED ACCOUNT LOGIN
            if (user.getIsDeleted() == 1) {
                return Map.of("success", false, "message", "This account has been deleted. Login not allowed.");
            }

//            // ⚠️ Optional: Prevent login when deletion request is pending
//            if (user.getDeletionReq() == 1) {
//                return Map.of("success", false, "message", "Account deletion is in progress. Login is disabled.");
//            }


            boolean valid = userDao.validateCustomer(accNo, pin);
            if (!valid) {
                int attempts = user.getFailedAttempts() + 1;
                int remaining = MAX_ATTEMPTS - attempts;

                return Map.of(
                        "success", false,
                        "message", "Invalid PIN.",
                        "failedAttempts", attempts,
                        "remainingAttempts", Math.max(0, remaining),
                        "locked", remaining <= 0
                );
            }

            String otp = otpService.generateOtp();
            otpService.sendOtp(accNo, user.getEmail(), user.getPhone(), otp);

            return Map.of(
                    "success", true,
                    "identifier", accNo,
                    "message", "OTP sent to registered email and mobile."
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------
    // ⭐ OTP VERIFY (FIXED TO SET FULL SESSION DATA)
    // ---------------------------------------------------
    @PostMapping("/otp/verify")
    public Map<String, Object> verifyOtp(
            @RequestBody Map<String, String> body,
            HttpSession session) {

        try {
            String identifier = body.get("identifier");
            String otp = body.get("otp");

            boolean verified = otpService.verifyOtp(identifier, otp);

            if (!verified) {
                return Map.of("success", false, "message", "Invalid or expired OTP.");
            }

            // --------------------------------------------
            // ADMIN LOGIN (username = identifier)
            // --------------------------------------------
            User admin = userDao.findByUsername(identifier);
            if (admin != null) {

                session.setAttribute("role", "ADMIN");
                session.setAttribute("identifier", identifier);

                // ⭐ ADDED
                session.setAttribute("username", identifier);

                return Map.of("success", true, "message", "OTP verified. Admin logged in.");
            }

            // --------------------------------------------
            // CUSTOMER LOGIN (identifier = accountNumber)
            // --------------------------------------------
            // --------------------------------------------
// CUSTOMER LOGIN (identifier = accountNumber)
// --------------------------------------------
            User customerUser = userDao.findByAccountNumber(identifier);

            if (customerUser != null) {

                // Fetch full account details
                Account account = accountDao.findByAccountNumber(identifier);

                session.setAttribute("role", "CUSTOMER");
                session.setAttribute("identifier", identifier);
                session.setAttribute("accountNumber", identifier);
                session.setAttribute("username", customerUser.getUsername());

                // ⭐⭐ RETURN ACCOUNT DETAILS ⭐⭐
                return Map.of(
                        "success", true,
                        "message", "OTP verified. Customer logged in.",
                        "role", "CUSTOMER",
                        "account", Map.of(
                                "accountNumber", account.getAccountNumber(),
                                "holderName", account.getHolderName(),
                                "email", account.getEmail(),
                                "phoneNumber", account.getPhoneNumber(),
                                "ifscCode", account.getIfscCode()
                        )
                );
            }


            return Map.of("success", false, "message", "User not found.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------
    // Everything below unchanged
    // ---------------------------------------------------

    @PostMapping("/forgot-pin/request")
    public Map<String, Object> forgotPinRequest(@RequestBody Map<String, String> body) {
        try {
            String accNo = body.get("accountNumber");
            String contact = body.get("contact");

            if (accNo == null || accNo.isBlank() ||
                    contact == null || contact.isBlank()) {
                return Map.of("success", false, "message", "Account number and contact are required.");
            }

            Account account = accountDao.findByAccountNumber(accNo);
            if (account == null)
                return Map.of("success", false, "message", "Account not found.");

            // ❌ BLOCK DELETED ACCOUNT FROM RESETTING PIN
            if (account.getIsDeleted() == 1) {
                return Map.of("success", false, "message", "This account is deleted. PIN reset is not allowed.");
            }

            boolean matches = accountDao.emailOrPhoneMatches(accNo, contact);
            if (!matches)
                return Map.of("success", false, "message", "Contact does not match records.");

            String otp = otpService.generateOtp();
            otpService.sendOtp(accNo, account.getEmail(), account.getPhoneNumber(), otp);

            return Map.of("success", true, "identifier", accNo, "message", "OTP sent.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    @PostMapping("/forgot-pin/verify")
    public Map<String, Object> forgotPinVerify(@RequestBody Map<String, String> body) {
        try {
            String accNo = body.get("accountNumber");
            String otp = body.get("otp");

            boolean verified = otpService.verifyOtp(accNo, otp);

            return Map.of(
                    "success", verified,
                    "message", verified ? "OTP verified." : "Invalid or expired OTP."
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    @PostMapping("/forgot-pin/reset")
    public Map<String, Object> forgotPinReset(@RequestBody Map<String, String> body) {
        try {
            String accNo = body.get("accountNumber");
            String newPin = body.get("newPin");

            if (accNo == null || accNo.isBlank() ||
                    newPin == null || newPin.isBlank()) {
                return Map.of("success", false, "message", "Account number and new PIN are required.");
            }

            boolean updated = userDao.setPinByAccount(accNo, newPin);

            return updated
                    ? Map.of("success", true, "message", "PIN reset successfully.")
                    : Map.of("success", false, "message", "Failed to reset PIN.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------
    // Forgot modal unified flow
    // ---------------------------------------------------

    @PostMapping("/forgot/request")
    public Map<String, Object> forgotRequest(@RequestBody Map<String, String> body) {
        try {
            String role = body.get("role");
            String identifier = body.get("identifier");
            String contact = body.get("contact");

            if (role == null || identifier == null || contact == null ||
                    role.isBlank() || identifier.isBlank() || contact.isBlank()) {
                return Map.of("success", false, "message", "All fields are required.");
            }

            if (role.equalsIgnoreCase("admin")) {
                User admin = userDao.findByUsername(identifier);

                if (admin == null)
                    return Map.of("success", false, "message", "Admin not found.");

                if (!contact.equals(admin.getEmail()) &&
                        !contact.equals(admin.getPhone())) {
                    return Map.of("success", false, "message", "Contact does not match records.");
                }

                String otp = otpService.generateOtp();
                otpService.sendOtp(identifier, admin.getEmail(), admin.getPhone(), otp);

                return Map.of("success", true, "identifier", identifier, "message", "OTP sent.");
            }

            if (role.equalsIgnoreCase("customer")) {

                Account acc = accountDao.findByAccountNumber(identifier);

                if (acc == null)
                    return Map.of("success", false, "message", "Account not found.");

                if (acc.getIsDeleted() == 1) {
                    return Map.of("success", false, "message", "This account is deleted. Recovery not allowed.");
                }

                boolean matches =
                        contact.equals(acc.getEmail()) ||
                                contact.equals(acc.getPhoneNumber());

                if (!matches)
                    return Map.of("success", false, "message", "Contact does not match records.");

                String otp = otpService.generateOtp();
                otpService.sendOtp(identifier, acc.getEmail(), acc.getPhoneNumber(), otp);

                return Map.of("success", true, "identifier", identifier, "message", "OTP sent.");
            }

            return Map.of("success", false, "message", "Invalid role.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    @PostMapping("/forgot/verify")
    public Map<String, Object> forgotVerify(@RequestBody Map<String, String> body) {
        try {
            String identifier = body.get("identifier");
            String otp = body.get("otp");

            boolean verified = otpService.verifyOtp(identifier, otp);

            return Map.of(
                    "success", verified,
                    "message", verified ? "OTP verified." : "Invalid or expired OTP."
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    @PostMapping("/forgot/reset")
    public Map<String, Object> forgotReset(@RequestBody Map<String, String> body) {
        try {
            String role = body.get("role");
            String identifier = body.get("identifier");
            String newPin = body.get("newPin");

            if (role == null || identifier == null || newPin == null ||
                    role.isBlank() || identifier.isBlank() || newPin.isBlank()) {
                return Map.of("success", false, "message", "All fields are required.");
            }

            boolean updated;

            if (role.equalsIgnoreCase("admin")) {
                updated = userDao.setPasswordByUsername(identifier, newPin);
                return Map.of("success", updated,
                        "message", updated ? "Password reset successfully." : "Failed to reset.");
            }

            if (role.equalsIgnoreCase("customer")) {
                updated = userDao.setPinByAccount(identifier, newPin);
                return Map.of("success", updated,
                        "message", updated ? "PIN reset successfully." : "Failed to reset.");
            }

            return Map.of("success", false, "message", "Invalid role.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------
    // TX PIN REQUEST
    // ---------------------------------------------------
    @PostMapping("/customer/transaction-pin/request")
    public Map<String, Object> requestTransactionPinOtp(@RequestBody Map<String, String> body) {
        try {
            String accNo = body.get("accountNumber");
            String contact = body.get("contact");

            if (accNo == null || accNo.isBlank() || contact == null || contact.isBlank()) {
                return Map.of("success", false, "message", "Account number and contact are required.");
            }

            Account account = accountDao.findByAccountNumber(accNo);

            if (account == null)
                return Map.of("success", false, "message", "Account not found.");

            boolean matches = accountDao.emailOrPhoneMatches(accNo, contact);

            if (!matches)
                return Map.of("success", false, "message", "Contact does not match records.");

            String otp = otpService.generateOtp();
            otpService.sendOtp(accNo, account.getEmail(), account.getPhoneNumber(), otp);

            return Map.of("success", true, "identifier", accNo, "message", "OTP sent.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------
    // TX PIN VERIFY
    // ---------------------------------------------------
    @PostMapping("/customer/transaction-pin/verify")
    public Map<String, Object> verifyTransactionPinOtp(@RequestBody Map<String, String> body) {
        try {
            String accNo = body.get("accountNumber");
            String otp = body.get("otp");

            if (accNo == null || accNo.isBlank() || otp == null || otp.isBlank()) {
                return Map.of("success", false, "message", "Account number and OTP are required.");
            }

            Account account = accountDao.findByAccountNumber(accNo);

            if (account == null)
                return Map.of("success", false, "message", "Account not found.");

            boolean verified = otpService.verifyOtp(accNo, otp);

            if (!verified) {
                int attempts = accountDao.getTxFailedAttempts(accNo) + 1;
                accountDao.setTxFailedAttempts(accNo, attempts);

                if (attempts >= 3) {
                    accountDao.lockTransactionForAccount(accNo);
                }

                return Map.of("success", false, "message", "Invalid or expired OTP.");
            }

            String newPin = String.format("%04d", secureRandom.nextInt(10000));

            boolean updated = accountDao.setTransactionPin(accNo, newPin);

            if (!updated) {
                return Map.of("success", false, "message",
                        "Failed to update transaction PIN. Try again later.");
            }

            return Map.of(
                    "success", true,
                    "message", "Transaction PIN generated successfully.",
                    "transactionPin", newPin
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }
}
