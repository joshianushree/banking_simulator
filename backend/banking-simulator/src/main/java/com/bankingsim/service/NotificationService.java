

package com.bankingsim.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private OtpService otpService;

    public void sendEmail(String to, String subject, String body) {
        otpService.sendEmail(to, subject, body);
    }

    public void sendSms(String phone, String message) {
        otpService.sendSms(phone, message);
    }
}
