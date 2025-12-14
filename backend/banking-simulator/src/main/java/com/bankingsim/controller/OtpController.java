package com.bankingsim.controller;

import com.bankingsim.service.OtpService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/otp")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    // ---------------------------------------------------------
    // SEND OTP TO PHONE NUMBER
    // ---------------------------------------------------------
    @PostMapping("/send")
    public Map<String, Object> sendOtp(@RequestBody Map<String, String> body) {

        String phone = body.get("contact");   // THIS IS NOW A PHONE NUMBER

        if (phone == null || phone.isBlank()) {
            return Map.of("success", false, "message", "Phone number is missing.");
        }

        // Fetch email linked to phone number
        Map<String, String> contact = otpService.getEmailAndAccByPhone(phone);

        if (contact == null) {
            return Map.of("success", false, "message", "Phone number not registered.");
        }

        String accNo = contact.get("accountNumber");
        String email = contact.get("email");

        // Generate and send OTP
        String otp = otpService.generateOtp();
        otpService.sendOtp(accNo, email, phone, otp);

        return Map.of("success", true, "message", "OTP sent successfully.");
    }

    // ---------------------------------------------------------
    // VERIFY OTP
    // ---------------------------------------------------------
    @PostMapping("/verify")
    public Map<String, Object> verifyOtp(@RequestBody Map<String, String> body) {

        String phone = body.get("contact");
        String otp = body.get("otp");

        if (phone == null || otp == null) {
            return Map.of("success", false, "message", "Missing OTP fields.");
        }

        // Lookup accountNumber for phone
        Map<String, String> contact = otpService.getEmailAndAccByPhone(phone);

        if (contact == null) {
            return Map.of("success", false, "message", "Phone not registered.");
        }

        String accNo = contact.get("accountNumber");

        boolean ok = otpService.verifyOtp(accNo, otp);

        return ok ? Map.of("success", true, "message", "OTP verified.")
                : Map.of("success", false, "message", "Invalid or expired OTP.");
    }
}
