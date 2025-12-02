package com.bankingsim.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Utility class for validating banking-related inputs such as
 * account numbers, holder names, emails, PINs, transaction amounts,
 * and the added fields: phoneNumber, IFSC, gender, address, govt ID types.
 */
public final class ValidationUtils {

    private ValidationUtils() {} // Prevent instantiation

    // === REGEX PATTERNS ===
    private static final Pattern ACC_NO_PATTERN = Pattern.compile("^\\d{11}$"); // exactly 11 digits
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z ]{3,}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern IFSC_PATTERN = Pattern.compile("^[A-Za-z]{4}0[A-Za-z0-9]{6}$");
    private static final Pattern ADDRESS_SAFE_PATTERN = Pattern.compile("^.{5,200}$");

    // === GOVT ID PATTERNS ===
    private static final Pattern AADHAR_PATTERN = Pattern.compile("^\\d{12}$");

    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

    private static final Pattern VOTER_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{7}$");

    // Driving License (Supports BOTH new & old formats)
    private static final Pattern DL_PATTERN = Pattern.compile(
            "^([A-Z]{2}[0-9]{2}[0-9]{4}[0-9]{7}|[A-Z]{2}-[0-9]{2}/[0-9]{4}/[0-9]{7})$"
    );

    // === ACCOUNT VALIDATIONS ===

    public static boolean isValidAccountNumber(String acc) {
        return acc != null && ACC_NO_PATTERN.matcher(acc.trim()).matches();
    }

    public static boolean isValidHolderName(String name) {
        return name != null && NAME_PATTERN.matcher(name.trim()).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidAccountType(String type) {
        if (type == null) return false;
        String t = type.trim().toUpperCase();
        return t.equals("SAVINGS") || t.equals("CURRENT") || t.equals("STUDENT");
    }

    public static boolean isValidInitialDeposit(String accType, BigDecimal amount) {
        if (amount == null) return false;
        String t = accType == null ? "" : accType.trim().toUpperCase();
        if (t.equals("STUDENT")) {
            return amount.compareTo(BigDecimal.ZERO) >= 0;
        } else {
            return amount.compareTo(new BigDecimal("1000")) >= 0;
        }
    }

    public static boolean isPositiveAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isValidPin(String pin) {
        return pin != null && PIN_PATTERN.matcher(pin.trim()).matches();
    }

    // === NEW FIELD VALIDATIONS ===

    public static boolean isValidPhoneNumber(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    public static boolean isValidIfsc(String ifsc) {
        if (ifsc == null || ifsc.trim().isEmpty()) return true; // auto-generated
        return IFSC_PATTERN.matcher(ifsc.trim().toUpperCase()).matches();
    }

    public static boolean isValidGender(String gender) {
        if (gender == null) return false;
        String g = gender.trim().toUpperCase();
        return g.equals("MALE") || g.equals("FEMALE") || g.equals("OTHER") || g.equals("PREFER_NOT_TO_SAY");
    }

    public static boolean isValidAddress(String address) {
        return address != null && ADDRESS_SAFE_PATTERN.matcher(address.trim()).matches();
    }

    // === GOVERNMENT ID VALIDATIONS ===

    public static boolean isValidAadhar(String aadhar) {
        return aadhar != null && AADHAR_PATTERN.matcher(aadhar.trim()).matches();
    }

    public static boolean isValidPAN(String pan) {
        return pan != null && PAN_PATTERN.matcher(pan.trim().toUpperCase()).matches();
    }

    public static boolean isValidVoterId(String voter) {
        return voter != null && VOTER_PATTERN.matcher(voter.trim().toUpperCase()).matches();
    }

    public static boolean isValidDrivingLicense(String dl) {
        return dl != null && DL_PATTERN.matcher(dl.trim().toUpperCase()).matches();
    }
}
