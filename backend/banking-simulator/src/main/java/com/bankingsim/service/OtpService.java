package com.bankingsim.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.math.BigDecimal;

@Service
public class OtpService {

    private final JdbcTemplate jdbcTemplate;

    public OtpService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Twilio
    @Value("${twilio.account-sid}")
    private String TWILIO_ACCOUNT_SID;

    @Value("${twilio.auth-token}")
    private String TWILIO_AUTH_TOKEN;

    @Value("${twilio.phone-number}")
    private String TWILIO_PHONE_NUMBER;

    // Email
    @Value("${mail.sender.email}")
    private String SENDER_EMAIL;

    @Value("${mail.sender.password}")
    private String SENDER_PASSWORD;

    @Value("${banking.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    private boolean twilioInitialized = false;

    private void initTwilio() {
        if (!twilioInitialized && TWILIO_ACCOUNT_SID != null) {
            try {
                Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
                twilioInitialized = true;
            } catch (Exception ignored) {}
        }
    }

    // ---------------------- OTP GENERATION -----------------------
    public String generateOtp() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    private void saveOtp(String identifier, String otp) {
        jdbcTemplate.update("DELETE FROM otp_verification WHERE user_identifier=?", identifier);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusMinutes(otpExpiryMinutes);

        jdbcTemplate.update("""
            INSERT INTO otp_verification 
            (user_identifier, otp_code, created_at, expires_at, verified)
            VALUES (?, ?, ?, ?, FALSE)
        """, identifier, otp,
                Timestamp.valueOf(now),
                Timestamp.valueOf(expiry)
        );
    }

    private void logOtpToConsole(String identifier, String otp) {
        System.out.println("\n====================================");
        System.out.println(" 🔐 OTP GENERATED");
        System.out.println(" User       : " + identifier);
        System.out.println(" OTP        : " + otp);
        System.out.println(" Valid For  : " + otpExpiryMinutes + " minutes");
        System.out.println("====================================\n");
    }

    // ---------------------- EMAIL OTP -----------------------
    private void sendOtpEmail(String toEmail, String otp) {
        String body =
                "Dear Customer,\n\n" +
                        "Your One-Time Password (OTP) has been generated successfully.\n\n" +
                        "📌 OTP DETAILS\n" +
                        "• OTP: " + otp + "\n" +
                        "• Valid For: " + otpExpiryMinutes + " minutes\n\n" +
                        "Please use this OTP to complete your verification.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmailInternal(toEmail, "Your OTP - AstroNova Bank", body);
    }

    // ---------------------- SMS OTP ------------------------
    private void sendOtpSms(String phoneNumber, String otp) {
        String message =
                "AstroNova Bank\n" +
                        "OTP: " + otp + "\n" +
                        "Valid for " + otpExpiryMinutes + " mins.\n" +
                        "Do not share with anyone.";

        sendSmsInternal(phoneNumber, message);
    }

    // ---------------------- SEND OTP ------------------------
    public void sendOtp(String identifier, String email, String phone, String otp) {
        saveOtp(identifier, otp);
        logOtpToConsole(identifier, otp);

        if (email != null && !email.isBlank()) sendOtpEmail(email, otp);
        if (phone != null && !phone.isBlank()) sendOtpSms(phone, otp);
    }

    // ---------------------- VERIFY OTP ----------------------
    public boolean verifyOtp(String identifier, String enteredOtp) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM otp_verification
                WHERE user_identifier=? 
                AND otp_code=? 
                AND verified=FALSE 
                AND expires_at > NOW()
            """, Integer.class, identifier, enteredOtp);

            if (count != null && count > 0) {
                jdbcTemplate.update("UPDATE otp_verification SET verified=TRUE WHERE user_identifier=?", identifier);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ======================================================================
    // Public Email / SMS Notification Methods
    // ======================================================================

    public void sendEmail(String to, String subject, String body) {
        sendEmailInternal(to, subject, body);
    }

    public void sendSms(String phone, String messageText) {
        sendSmsInternal(phone, messageText);
    }

    // ======================================================================
    // ACCOUNT CREATION EMAIL
    // ======================================================================

    public void sendAccountCreationMail(String email, String holderName, String accNo,
                                        String type, BigDecimal balance, String ifscCode) {
        try {
            String subject = "AstroNova Bank - Account Created Successfully";

            String body =
                    "Dear " + holderName + ",\n\n" +
                            "Your new AstroNova Bank account has been created successfully.\n\n" +
                            "📌 ACCOUNT DETAILS\n" +
                            "• Account Number: " + accNo + "\n" +
                            "• IFSC Code: " + ifscCode + "\n" +
                            "• Account Type: " + type + "\n" +
                            "• Opening Balance: ₹" + balance + "\n\n" +
                            "Regards,\nAstroNova Bank";

            sendEmail(email, subject, body);

        } catch (Exception e) {
            System.err.println("⚠️ Failed to send account creation email: " + e.getMessage());
        }
    }

    // ======================================================================
    // INTERNAL EMAIL / SMS IMPLEMENTATION
    // ======================================================================

    private void sendEmailInternal(String toEmail, String subject, String body) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            System.out.println("📧 Email sent → " + toEmail);

        } catch (Exception e) {
            System.err.println("❌ Email failed: " + e.getMessage());
        }
    }

    private void sendSmsInternal(String phoneNumber, String text) {
        initTwilio();

        if (twilioInitialized) {
            try {
                new MessageCreator(
                        new PhoneNumber(phoneNumber),
                        new PhoneNumber(TWILIO_PHONE_NUMBER),
                        text
                ).create();

                System.out.println("📱 SMS sent → " + phoneNumber);
                return;

            } catch (Exception e) {
                System.err.println("⚠️ SMS failed — fallback to simulation");
            }
        }

        // Fallback simulated SMS
        System.out.println("📱 [SIMULATED SMS] " + phoneNumber + " → " + text);
    }

    // ======================================================================
    // ⭐⭐ EXTRA FUNCTIONS ADDED FOR ACCOUNT DELETION EMAILS ⭐⭐
    // ======================================================================

    public void sendDeletionRequestEmail(String email, String holder, String accNo, String reason) {
        String subject = "AstroNova Bank – Account Deletion Request Received";

        String body =
                "Dear " + holder + ",\n\n" +
                        "We have received your account deletion request for account:\n" +
                        "• Account Number: " + accNo + "\n\n" +
                        "Reason Provided:\n" +
                        reason + "\n\n" +
                        "Our team will review the request within 48 hours.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmail(email, subject, body);
    }

    public void sendDeletionApprovedEmail(String email, String holder, String accNo, String comment) {
        String subject = "AstroNova Bank – Account Deletion Approved";

        String body =
                "Dear " + holder + ",\n\n" +
                        "Your account deletion request for account:\n" +
                        "• " + accNo + "\n\n" +
                        "has been APPROVED.\n\n" +
                        "Admin Comment:\n" +
                        comment + "\n\n" +
                        "Your account is now closed.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmail(email, subject, body);
    }

    public void sendDeletionRejectedEmail(String email, String holder, String accNo, String comment) {
        String subject = "AstroNova Bank – Account Deletion Request Rejected";

        String body =
                "Dear " + holder + ",\n\n" +
                        "Your account deletion request for account:\n" +
                        "• " + accNo + "\n\n" +
                        "has been REJECTED.\n\n" +
                        "Admin Comment:\n" +
                        comment + "\n\n" +
                        "You may update details and reapply anytime.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmail(email, subject, body);
    }
    // ======================================================================
    // ⭐⭐ LOAN EMAIL HELPERS ⭐⭐
    // ======================================================================

    public void sendLoanRequestSubmittedMail(String email,
                                             String holderName,
                                             String accNo,
                                             BigDecimal amount,
                                             String loanType,
                                             String emiPlan) {
        if (email == null || email.isBlank()) return;

        String subject = "AstroNova Bank – Loan Request Submitted";

        String body =
                "Dear " + holderName + ",\n\n" +
                        "Your loan request has been submitted successfully.\n\n" +
                        "📌 LOAN REQUEST DETAILS\n" +
                        "• Account Number: " + accNo + "\n" +
                        "• Loan Amount: ₹" + amount + "\n" +
                        "• Loan Type: " + loanType + "\n" +
                        "• EMI Plan: " + emiPlan + "\n\n" +
                        "Our team will review your request and notify you once it is processed.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmail(email, subject, body);
    }

    public void sendLoanApprovedMail(String email,
                                     String holderName,
                                     String accNo,
                                     BigDecimal amount,
                                     String loanType,
                                     String emiPlan) {
        if (email == null || email.isBlank()) return;

        String subject = "AstroNova Bank – Loan Approved";

        String body =
                "Dear " + holderName + ",\n\n" +
                        "Good news! Your loan request has been APPROVED.\n\n" +
                        "📌 LOAN DETAILS\n" +
                        "• Account Number: " + accNo + "\n" +
                        "• Sanctioned Amount: ₹" + amount + "\n" +
                        "• Loan Type: " + loanType + "\n" +
                        "• EMI Plan: " + emiPlan + "\n\n" +
                        "The amount has been credited to your account.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmail(email, subject, body);
    }

    public void sendLoanRejectedMail(String email,
                                     String holderName,
                                     String accNo,
                                     BigDecimal amount,
                                     String loanType,
                                     String emiPlan,
                                     String adminComment) {
        if (email == null || email.isBlank()) return;

        String subject = "AstroNova Bank – Loan Request Rejected";

        String body =
                "Dear " + holderName + ",\n\n" +
                        "We regret to inform you that your loan request has been REJECTED.\n\n" +
                        "📌 LOAN REQUEST DETAILS\n" +
                        "• Account Number: " + accNo + "\n" +
                        "• Requested Amount: ₹" + amount + "\n" +
                        "• Loan Type: " + loanType + "\n" +
                        "• EMI Plan: " + emiPlan + "\n\n" +
                        "Reason / Comment from Bank:\n" +
                        (adminComment == null || adminComment.isBlank() ? "Not specified" : adminComment) + "\n\n" +
                        "You may update your details and re-apply at any time.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmail(email, subject, body);
    }

    public void sendLoanClosedMail(String email,
                                   String holderName,
                                   String accNo,
                                   BigDecimal paidAmount,
                                   BigDecimal newBalance) {
        if (email == null || email.isBlank()) return;

        String subject = "AstroNova Bank – Loan Closed Successfully";

        String body =
                "Dear " + holderName + ",\n\n" +
                        "Your loan has been fully repaid and closed successfully.\n\n" +
                        "📌 PAYMENT DETAILS\n" +
                        "• Account Number: " + accNo + "\n" +
                        "• Amount Paid: ₹" + paidAmount + "\n" +
                        "• Available Balance After Payment: ₹" + newBalance + "\n\n" +
                        "Thank you for banking with AstroNova.\n\n" +
                        "Regards,\nAstroNova Bank";

        sendEmail(email, subject, body);
    }

    // ----------------------------------------------
    // HELPERS TO FETCH CONTACT INFO
    // ----------------------------------------------
        @Autowired
        private JdbcTemplate jdbc;

        public String getEmailByIdentifier(String accNo) {
            try {
                return jdbc.queryForObject(
                        "SELECT email FROM accounts WHERE account_number = ?",
                        String.class,
                        accNo
                );
            } catch (Exception e) { return null; }
        }

        public String getPhoneByIdentifier(String accNo) {
            try {
                return jdbc.queryForObject(
                        "SELECT phone_number FROM accounts WHERE account_number = ?",
                        String.class,
                        accNo
                );
            } catch (Exception e) { return null; }
        }

        public Map<String, String> getEmailAndAccByPhone(String phone) {
            try {
                return jdbcTemplate.queryForObject("""
                    SELECT account_number, email 
                    FROM accounts 
                    WHERE phone_number = ?
                """,
                        (rs, rowNum) -> Map.of(
                                "accountNumber", rs.getString("account_number"),
                                "email", rs.getString("email")
                        ),
                        phone
                );
            } catch (Exception e) {
                return null;
            }
        }

}
