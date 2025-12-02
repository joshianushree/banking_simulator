package com.bankingsim.controller;

import com.bankingsim.dao.UserDao;
import com.bankingsim.model.Account;
import com.bankingsim.model.User;
import com.bankingsim.service.AccountManager;
import com.bankingsim.util.BCryptUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.time.LocalDate;
import java.time.Period;

import static com.bankingsim.util.PdfGenerator.accountDao;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AccountController {

    @Autowired private AccountManager accountManager;
    @Autowired private UserDao userDao;

    private final Random random = new Random();

    private String safeError(Throwable e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank())
                ? "Unexpected server error occurred."
                : msg;
    }

    // ---------------------------------------------------------------------------------
    // 1️⃣ CREATE CUSTOMER ACCOUNT — MULTIPART FORM-DATA VERSION (NEW)
    // ---------------------------------------------------------------------------------
    @PostMapping(value = "/create-customer", consumes = "multipart/form-data")
    public Map<String, Object> createCustomer(
            @RequestParam String holderName,
            @RequestParam String email,
            @RequestParam String phoneNumber,
            @RequestParam String address,
            @RequestParam String gender,
            @RequestParam String accountType,
            @RequestParam String pin,
            @RequestParam String pinConfirm,
            @RequestParam String dob,
            @RequestParam String branchName,
            @RequestParam String govtIdType,
            @RequestParam String govtIdNumber,
            @RequestParam("govtIdProof") MultipartFile govtIdProofFile,
            @RequestParam(required = false, defaultValue = "0") BigDecimal balance
    ) {

        try {
            // ⭐ ORIGINAL BASIC VALIDATIONS (unchanged)
            if (holderName.isBlank()) return Map.of("success", false, "message", "Holder name is required.");
            if (email.isBlank()) return Map.of("success", false, "message", "Email is required.");
            if (phoneNumber.isBlank()) return Map.of("success", false, "message", "Phone number is required.");
            if (address.isBlank()) return Map.of("success", false, "message", "Address is required.");
            if (gender.isBlank()) return Map.of("success", false, "message", "Gender is required.");
            if (accountType.isBlank()) return Map.of("success", false, "message", "Account type is required.");

            if (pin.isBlank() || pinConfirm.isBlank())
                return Map.of("success", false, "message", "PIN and Confirm PIN are required.");

            if (!pin.equals(pinConfirm))
                return Map.of("success", false, "message", "PIN and Confirm PIN must match.");

            // ⭐ DOB HANDLING (same as original)
            LocalDate dobDate;
            try {
                dobDate = LocalDate.parse(dob);
            } catch (Exception ex) {
                return Map.of("success", false, "message", "Invalid date of birth format.");
            }

            int age = Period.between(dobDate, LocalDate.now()).getYears();

            // ⭐ Govt ID Proof Check
            if (govtIdProofFile == null || govtIdProofFile.isEmpty())
                return Map.of("success", false, "message", "Govt ID proof file is required.");

            byte[] govtIdProofBytes = govtIdProofFile.getBytes();

            // ⭐ Create account object (same as original + new fields)
            String hashedLoginPin = BCryptUtil.hashPassword(pin);

            Account acc = new Account(
                    null,
                    holderName,
                    email,
                    balance,
                    accountType,
                    phoneNumber,
                    gender,
                    address,
                    hashedLoginPin
            );

            // ⭐ CORE FIELDS
            acc.setDob(dobDate);
            acc.setAge(age);

            // ⭐ NEW FIELDS
            acc.setBranchName(branchName);
            acc.setGovtIdType(govtIdType);
            acc.setGovtIdNumber(govtIdNumber);
            acc.setGovtIdProof(govtIdProofBytes);

            // ⭐ Auto-generate 4-digit Transaction PIN (unchanged)
            String generatedTxPin = String.format("%04d", random.nextInt(10000));
            acc.setTransactionPin(BCryptUtil.hashPassword(generatedTxPin));

            // ⭐ MAIN ACCOUNT CREATION (unchanged internal logic)
            accountManager.createAccount(acc);

            // ⭐ Create linked user (unchanged)
            accountManager.createUserForAccount(acc, "CUSTOMER");

            return Map.of(
                    "success", true,
                    "message", "Customer account created successfully.",
                    "accountNumber", acc.getAccountNumber(),
                    "generatedPin", generatedTxPin
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------------------------------------
    // 2️⃣ CREATE ADMIN ACCOUNT (UNCHANGED)
    // ---------------------------------------------------------------------------------
    @PostMapping("/create-admin")
    public Map<String, Object> createAdmin(@RequestBody Map<String, Object> body,
                                           HttpSession session) {

        try {
            if (!"ADMIN".equals(session.getAttribute("role")))
                return Map.of("success", false, "message", "Unauthorized. Only Admin can create Admin.");

            String holderName = (String) body.get("holderName");
            String email = (String) body.get("email");
            String phone = (String) body.get("phoneNumber");
            String password = (String) body.get("password");
            String passwordConfirm = (String) body.get("passwordConfirm");

            if (holderName == null || holderName.isBlank())
                return Map.of("success", false, "message", "Holder name is required.");

            if (email == null || email.isBlank())
                return Map.of("success", false, "message", "Email is required.");

            if (phone == null || phone.isBlank())
                return Map.of("success", false, "message", "Phone number is required.");

            if (password == null || password.isBlank())
                return Map.of("success", false, "message", "Password is required.");

            if (!password.equals(passwordConfirm))
                return Map.of("success", false, "message", "Passwords do not match.");

            String hashedPassword = BCryptUtil.hashPassword(password);

            String baseUsername = holderName.trim().toLowerCase().replaceAll("\\s+", "");
            String username = baseUsername;

            int counter = 1;
            while (userDao.usernameExists(username)) {
                username = baseUsername + counter;
                counter++;
            }

            User admin = new User();
            admin.setUsername(username);
            admin.setPassword(hashedPassword);
            admin.setEmail(email);
            admin.setPhone(phone);
            admin.setRole("ADMIN");
            admin.setCreatedAt(LocalDateTime.now());

            userDao.createUser(admin);

            return Map.of(
                    "success", true,
                    "message", "Admin created successfully.",
                    "username", username
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------------------------------------
    // GETTERS — UNCHANGED
    // ---------------------------------------------------------------------------------

    @GetMapping
    public List<Account> getAllAccounts() {
        return accountManager.listAllAccounts();
    }

    @GetMapping("/locked")
    public List<Account> getLockedAccounts() {
        return accountManager.listAllAccounts()
                .stream()
                .filter(Account::isLocked)
                .toList();
    }

    @GetMapping("/{accNo}")
    public Account getAccount(@PathVariable String accNo) {
        return accountManager.findAccountByNumber(accNo);
    }

    @PatchMapping("/{accNo}/unlock")
    public Map<String, Object> unlockAccount(@PathVariable String accNo) {
        try {
            userDao.unlockAccount(accNo);
            return Map.of("success", true, "message", "Account unlocked.");
        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------------------------------------
    // CONTACT UPDATE — UNCHANGED
    // ---------------------------------------------------------------------------------
    @PutMapping("/{accNo}/contact")
    public Map<String, Object> updateContact(
            @PathVariable String accNo,
            @RequestBody Map<String, String> body) {

        try {
            String email = body.get("email");
            String phone = body.get("phoneNumber");

            if (email == null || email.isBlank())
                return Map.of("success", false, "message", "Email is required.");

            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
                return Map.of("success", false, "message", "Invalid email format.");

            if (phone == null || phone.isBlank())
                return Map.of("success", false, "message", "Phone number is required.");

            if (!phone.matches("\\d{10}"))
                return Map.of("success", false, "message", "Phone number must be exactly 10 digits.");

            Account acc = accountManager.findAccountByNumber(accNo);
            if (acc == null)
                return Map.of("success", false, "message", "Account not found.");

            accountManager.updateContactInfo(accNo, email, phone);

            return Map.of(
                    "success", true,
                    "message", "Contact updated.",
                    "account", accountManager.findAccountByNumber(accNo)
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }

    // ---------------------------------------------------------------------------------
    // UPDATE CUSTOMER DETAILS — UNCHANGED
    // ---------------------------------------------------------------------------------
    @PutMapping("/{accNo}")
    public Map<String, Object> updateCustomerDetails(
            @PathVariable String accNo,
            @RequestBody Map<String, Object> body) {

        try {
            Account acc = accountManager.findAccountByNumber(accNo);
            if (acc == null)
                return Map.of("success", false, "message", "Account not found.");

            if (body.containsKey("holderName"))
                acc.setHolderName((String) body.get("holderName"));

            if (body.containsKey("address"))
                acc.setAddress((String) body.get("address"));

            if (body.containsKey("gender"))
                acc.setGender((String) body.get("gender"));

            if (body.containsKey("accountType"))
                acc.setAccountType((String) body.get("accountType"));

            accountManager.updateCustomerDetails(acc);

            return Map.of(
                    "success", true,
                    "message", "Customer details updated successfully.",
                    "account", accountManager.findAccountByNumber(accNo)
            );

        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    // ---------------------------
// CUSTOMER SELF — SOFT DELETE
// ---------------------------
    /**
     * Customer can soft-delete (mark DELETED) their own account.
     * Required body fields:
     *  - holderName
     *  - accountNumber
     *  - ifsc
     *  - contact  (email or phone)
     *  - pin
     *  - reason
     *
     * Security checks:
     *  - must be logged in and session must belong to the same account (or username mapped to that account)
     *  - holderName, IFSC and contact must match stored values
     *  - PIN must match (checked against users.password via BCrypt)
     *
     * On success:
     *  - account and user rows are updated to status='DELETED', is_locked=TRUE, logout_time set
     *  - audit entry added by AccountManager
     */
    @PostMapping("/delete")
    public Map<String, Object> deleteAccount(@RequestBody Map<String, String> body, HttpSession session) {
        try {
            String holderName = Optional.ofNullable(body.get("holderName")).orElse("").trim();
            String accNo = Optional.ofNullable(body.get("accountNumber")).orElse("").trim();
            String ifsc = Optional.ofNullable(body.get("ifsc")).orElse("").trim();
            String contact = Optional.ofNullable(body.get("contact")).orElse("").trim();
            String pin = Optional.ofNullable(body.get("pin")).orElse("").trim();
            String reason = Optional.ofNullable(body.get("reason")).orElse("").trim();

            // Required fields check
            if (holderName.isBlank() || accNo.isBlank() || ifsc.isBlank() || contact.isBlank() || pin.isBlank() || reason.isBlank()) {
                return Map.of("success", false, "message", "All fields are required.");
            }

            // ----------------------------
            // SESSION VALIDATION
            // ----------------------------
            Object sessAccObj = session.getAttribute("accountNumber");
            Object sessUserObj = session.getAttribute("username");

            boolean sessionMatches = false;

            if (sessAccObj != null && accNo.equals(String.valueOf(sessAccObj))) {
                sessionMatches = true;
            }

            if (!sessionMatches && sessUserObj != null) {
                // username → accountNumber resolver
                String sessUsername = String.valueOf(sessUserObj);
                var sessUser = userDao.findByUsername(sessUsername);
                if (sessUser != null && accNo.equals(sessUser.getAccountNumber())) {
                    sessionMatches = true;
                }
            }

            if (!sessionMatches) {
                return Map.of("success", false, "message", "Unauthorized: you may only delete your own account.");
            }

            // ----------------------------
            // LOAD ACCOUNT
            // ----------------------------
            Account acc = accountManager.findAccountByNumber(accNo);
            if (acc == null) return Map.of("success", false, "message", "Account not found.");

            // ----------------------------
            // BASIC FIELD MATCHING
            // ----------------------------
            if (!acc.getHolderName().trim().equalsIgnoreCase(holderName)) {
                return Map.of("success", false, "message", "Holder name does not match.");
            }

            if (acc.getIfscCode() == null || !acc.getIfscCode().trim().equalsIgnoreCase(ifsc)) {
                return Map.of("success", false, "message", "IFSC code does not match.");
            }

            // ----------------------------
            // CONTACT MATCHING (RECOMMENDED ONE-LINE VERSION)
            // ----------------------------
            if (!accountDao.emailOrPhoneMatches(accNo, contact)) {
                return Map.of("success", false, "message", "Email or phone does not match our records.");
            }

            // ----------------------------
            // PIN VERIFICATION
            // ----------------------------
            var user = userDao.findByAccountNumber(accNo);
            if (user == null)
                return Map.of("success", false, "message", "User linked to account not found.");

            if (!BCryptUtil.verifyPassword(pin, user.getPassword())) {
                return Map.of("success", false, "message", "Invalid PIN.");
            }

            // ----------------------------
            // OPTIONAL: mini-statement backup
            // ----------------------------
            try {
                accountManager.generateMiniStatementPdf(accNo);
            } catch (Exception ignored) {}

            // ----------------------------
            // SOFT DELETE EXECUTION
            // ----------------------------
            boolean ok = accountManager.softDeleteCustomerAccount(
                    accNo,
                    reason,
                    user.getUsername() != null ? user.getUsername() : acc.getHolderName()
            );

            if (ok) {
                session.invalidate(); // logout user
                return Map.of("success", true, "message", "Account deletion successful.");
            } else {
                return Map.of("success", false, "message", "Failed to delete account.");
            }

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }


// ---------------------------
// ADMIN ONLY: RESTORE SOFT-DELETED ACCOUNT
// ---------------------------
    /**
     * Admin endpoint to restore soft-deleted account.
     * Only accessible by Admin session (checks session.role == "ADMIN").
     */
    @PatchMapping("/restore/{accNo}")
    public Map<String, Object> restoreAccount(@PathVariable String accNo, HttpSession session) {
        try {
            Object role = session.getAttribute("role");
            if (role == null || !"ADMIN".equals(String.valueOf(role))) {
                return Map.of("success", false, "message", "Unauthorized. Admins only.");
            }

            Account existing = accountManager.findAccountByNumber(accNo);
            if (existing == null) return Map.of("success", false, "message", "Account not found.");

            boolean ok = accountManager.restoreSoftDeletedAccount(accNo, String.valueOf(session.getAttribute("username") != null ? session.getAttribute("username") : "ADMIN"));
            if (ok) return Map.of("success", true, "message", "Account restored.");
            else return Map.of("success", false, "message", "Restore failed.");

        } catch (Exception e) {
            return Map.of("success", false, "message", safeError(e));
        }
    }


}
