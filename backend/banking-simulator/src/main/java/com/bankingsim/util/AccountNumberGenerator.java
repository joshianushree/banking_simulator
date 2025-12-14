package com.bankingsim.util;

import java.security.SecureRandom;

public class AccountNumberGenerator {
    private static final SecureRandom rnd = new SecureRandom();

    /**
     * Generate an 11-digit numeric account number (first digit non-zero).
     */
    public static String generate11Digit() {
        StringBuilder sb = new StringBuilder(11);
        // first digit 1-9
        sb.append(rnd.nextInt(9) + 1);
        for (int i = 1; i < 11; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }
}
