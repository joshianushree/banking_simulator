package com.bankingsim.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Utility class for password hashing and verification using BCrypt (Spring Security).
 * ✅ Automatically hashes new passwords with salt.
 * ✅ Safely compares hashed passwords.
 * ✅ Detects legacy plaintext passwords and can migrate them on next login.
 */
public final class BCryptUtil {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private BCryptUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Hash a plaintext password using BCrypt with salt.
     */
    public static String hashPassword(String plainTextPassword) {
        if (plainTextPassword == null || plainTextPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank");
        }
        return ENCODER.encode(plainTextPassword);
    }

    /**
     * Verify a plaintext password against a stored hash.
     * - Supports legacy plaintext fallback (for old accounts).
     * - Returns true if the plaintext matches the hashed or legacy stored password.
     */
    public static boolean verifyPassword(String plainText, String stored) {
        if (plainText == null || stored == null) return false;

        // 🔹 Case 1: stored value looks like a BCrypt hash
        if (isHashed(stored)) {
            try {
                return ENCODER.matches(plainText, stored);
            } catch (Exception e) {
                return false;
            }
        }

        // 🔹 Case 2: stored value is plaintext (legacy password)
        return plainText.equals(stored);
    }

    /**
     * Check whether a stored password looks like a BCrypt hash.
     */
    public static boolean isHashed(String storedPassword) {
        if (storedPassword == null) return false;
        return storedPassword.startsWith("$2a$")
                || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$");
    }
}
