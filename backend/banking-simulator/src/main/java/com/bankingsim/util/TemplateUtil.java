package com.bankingsim.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class TemplateUtil {

    private static String formatINR(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        return nf.format(amount);
    }

    public static String depositMessage(String holder, String accNo, BigDecimal amount,
                                        BigDecimal balance, String category) {

        return "Dear " + holder + ",\n\n" +
                "💰 Deposit Received\n\n" +
                "📌 TRANSACTION DETAILS\n" +
                "• Amount Deposited: " + formatINR(amount) + "\n" +
                "• Account: " + accNo + "\n" +
                "• Category: " + category + "\n" +
                "• Current Balance: " + formatINR(balance) + "\n\n" +
                "Thank you for banking with AstroNova.\n\n" +
                "Regards,\nAstroNova Bank";
    }

    public static String withdrawalMessage(String holder, String accNo, BigDecimal amount,
                                           BigDecimal balance, String category) {

        return "Dear " + holder + ",\n\n" +
                "💸 Withdrawal Processed\n\n" +
                "📌 TRANSACTION DETAILS\n" +
                "• Amount Withdrawn: " + formatINR(amount) + "\n" +
                "• Account: " + accNo + "\n" +
                "• Category: " + category + "\n" +
                "• Current Balance: " + formatINR(balance) + "\n\n" +
                "If this was not you, contact support immediately.\n\n" +
                "Regards,\nAstroNova Bank";
    }

    public static String transferSenderMessage(String holder, String fromAcc, String toAcc,
                                               BigDecimal amount, BigDecimal balance,
                                               String category) {

        return "Dear " + holder + ",\n\n" +
                "✅ Transfer Successful\n\n" +
                "📌 TRANSACTION DETAILS\n" +
                "• Amount: " + formatINR(amount) + "\n" +
                "• From Account: " + fromAcc + "\n" +
                "• To Account: " + toAcc + "\n" +
                "• Category: " + category + "\n" +
                "• Remaining Balance: " + formatINR(balance) + "\n\n" +
                "If you did not authorize this, contact support immediately.\n\n" +
                "Regards,\nAstroNova Bank";
    }

    public static String transferReceiverMessage(String holder, String fromAcc, String toAcc,
                                                 BigDecimal amount, BigDecimal balance,
                                                 String category) {

        return "Dear " + holder + ",\n\n" +
                "💰 Amount Received\n\n" +
                "📌 TRANSACTION DETAILS\n" +
                "• Amount: " + formatINR(amount) + "\n" +
                "• From Account: " + fromAcc + "\n" +
                "• To Account: " + toAcc + "\n" +
                "• Category: " + category + "\n" +
                "• Current Balance: " + formatINR(balance) + "\n\n" +
                "Thank you for banking with AstroNova.\n\n" +
                "Regards,\nAstroNova Bank";
    }
}
