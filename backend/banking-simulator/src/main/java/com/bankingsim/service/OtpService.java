package com.bankingsim.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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

    // ---------------------- SEND OTP (PUBLIC) ------------------------
    public void sendOtp(String identifier, String email, String phone, String otp) {
        saveOtp(identifier, otp);
        logOtpToConsole(identifier, otp);

        if (email != null && !email.isBlank()) {
            sendOtpEmail(email, otp);
        }

        if (phone != null && !phone.isBlank()) {
            sendOtpSms(phone, otp);
        }
    }

    // ---------------------- VERIFY OTP ------------------------------
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
    // NEW: ACCOUNT CREATION EMAIL (NO SMS)
    // ======================================================================

    public void sendAccountCreationMail(String email, String holderName, String accNo, String type, BigDecimal balance,String ifscCode) {
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
                            "You can now log in and start banking.\n\n" +
                            "Regards,\nAstroNova Bank";

            sendEmail(email, subject, body);

        } catch (Exception e) {
            System.err.println("⚠️ Failed to send account creation email: " + e.getMessage());
        }
    }

    // ======================================================================
    // INTERNAL EMAIL AND SMS IMPLEMENTATION
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
}
