package com.bankingsim.util;

import com.bankingsim.dao.AccountDao;
import com.bankingsim.model.Account;
import com.bankingsim.model.TransactionRecord;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class PdfGenerator {

    // ---------------------------------------------------
    // STATIC DAO INJECTION (optional) - set once at startup
    // ---------------------------------------------------
    public static AccountDao accountDao;

    public static void setAccountDao(AccountDao dao) {
        accountDao = dao;
    }

    // ---------------------------------------------------
    // PUBLIC METHODS - return PDF as bytes (encrypted or not)
    // ---------------------------------------------------

    public static byte[] generateAccountsPdfBytes(List<Account> accounts, String key) {
        byte[] raw = buildAccountsPdf(accounts);
        return encryptIfNeeded(raw, key);
    }

    public static byte[] generateTransactionsPdfBytes(List<TransactionRecord> transactions, String key) {
        byte[] raw = buildTransactionsPdf(transactions);
        return encryptIfNeeded(raw, key);
    }

    /**
     * Mini-statement signature kept as original: accNo + transactions + key.
     * The method will look up the Account via the static AccountDao if it has been set.
     */
    public static byte[] generateMiniStatementPdfBytes(String accNo, List<TransactionRecord> transactions, String key) {
        byte[] raw = buildMiniStatementPdf(accNo, transactions);
        return encryptIfNeeded(raw, key);
    }

    // ---------------------------------------------------
    // LEGACY NO-OPs (KEEP FOR COMPATIBILITY)
    // ---------------------------------------------------

    public static void generateAccountsPdf(List<Account> accounts) {}
    public static void generateTransactionsPdf(List<TransactionRecord> transactions) {}
    public static void generateMiniStatementPdf(String accNo, List<TransactionRecord> transactions) {}

    // ---------------------------------------------------
    // LOAD LOGO (from resources/static/logo.png)
    // ---------------------------------------------------
    private static Image loadLogo() {
        try {
            // load from classpath: src/main/resources/static/logo.png
            return Image.getInstance(PdfGenerator.class.getResource("/static/logo.png"));
        } catch (Exception e) {
            // don't fail PDF generation if logo missing — log and continue
            System.err.println("⚠ Logo not found or failed to load: " + e.getMessage());
            return null;
        }
    }

    private static void addLogoAndTitle(Document document) throws Exception {
        Image logo = loadLogo();
        if (logo != null) {
            logo.scaleToFit(80, 80);
            logo.setAlignment(Element.ALIGN_CENTER);
            document.add(logo);
        }

        Paragraph bankTitle = new Paragraph("AstroNova Bank\n\n",
                new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD));
        bankTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(bankTitle);
    }
    private static String prettyType(Object type) {
        if (type == null) return "-";

        // Convert ENUM or plain string to uniform string
        String raw = type.toString().trim();

        // Normalize: convert spaces → underscore so formatting works
        raw = raw.replace(" ", "_").toUpperCase();

        // Convert "LOAN_REPAYMENT" → "Loan repayment"
        String formatted = raw.replace("_", " ").toLowerCase();

        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }



    // ---------------------------------------------------
    // BUILD ACCOUNTS PDF (with IFSC column)
    // ---------------------------------------------------
    private static byte[] buildAccountsPdf(List<Account> accounts) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            addLogoAndTitle(document);

            Paragraph titlePara = new Paragraph("All Accounts Report\n\n",
                    new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD));
            titlePara.setAlignment(Element.ALIGN_CENTER);
            document.add(titlePara);

            // 7 columns: Acc No, Holder, Email, Balance, Type, Status, IFSC Code
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);

            addHeader(table,
                    new String[]{"Acc No", "Holder", "Email", "Balance", "Type", "Status", "IFSC Code"},
                    new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD)
            );

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 10);

            if (accounts != null) {
                for (Account a : accounts) {
                    table.addCell(new Phrase(safe(a.getAccountNumber()), bodyFont));
                    table.addCell(new Phrase(safe(a.getHolderName()), bodyFont));
                    table.addCell(new Phrase(safe(a.getEmail()), bodyFont));
                    table.addCell(new Phrase("₹" + (a.getBalance() != null ? a.getBalance() : "0.00"), bodyFont));
                    table.addCell(new Phrase(safe(a.getAccountType()), bodyFont));
                    table.addCell(new Phrase(safe(a.getStatus()), bodyFont));
                    table.addCell(new Phrase(safe(a.getIfscCode()), bodyFont)); // IFSC
                }
            }

            document.add(table);
            document.add(new Paragraph("\nGenerated by AstroNova Bank © " + java.time.LocalDate.now()));
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            // return empty PDF bytes on error to avoid breaking caller logic
            e.printStackTrace();
            return new byte[0];
        }
    }

    // ---------------------------------------------------
    // BUILD TRANSACTIONS PDF (logo/title added)
    // ---------------------------------------------------
    private static byte[] buildTransactionsPdf(List<TransactionRecord> transactions) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            addLogoAndTitle(document);

            Paragraph titlePara = new Paragraph("All Transactions Report\n\n",
                    new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD));
            titlePara.setAlignment(Element.ALIGN_CENTER);
            document.add(titlePara);

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);

            addHeader(table,
                    new String[]{"Tx ID", "Date", "Type", "Amount", "From Acc", "To Acc", "Category", "Status"},
                    new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD)
            );

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 10);

            if (transactions != null) {
                for (TransactionRecord tx : transactions) {
                    table.addCell(new Phrase(safe(tx.getTxId()), bodyFont));
                    table.addCell(new Phrase(tx.getFormattedDate(), bodyFont));
                    table.addCell(new Phrase(prettyType(tx.getTxType()), bodyFont));
                    table.addCell(new Phrase("₹" + (tx.getAmount() != null ? tx.getAmount() : "0.00"), bodyFont));
                    table.addCell(new Phrase(safe(tx.getFromAccount()), bodyFont));
                    table.addCell(new Phrase(safe(tx.getToAccount()), bodyFont));
                    table.addCell(new Phrase(safe(tx.getCategory()), bodyFont));
                    table.addCell(new Phrase(safe(tx.getStatus()), bodyFont));
                }
            }

            document.add(table);
            document.add(new Paragraph("\nGenerated by AstroNova Bank © " + java.time.LocalDate.now()));
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    // ---------------------------------------------------
    // BUILD MINI STATEMENT (with detailed header block)
    // ---------------------------------------------------
    private static byte[] buildMiniStatementPdf(String accNo, List<TransactionRecord> transactions) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Use portrait A4 for minis
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            addLogoAndTitle(document);

            Paragraph titlePara = new Paragraph("Mini Statement\n\n",
                    new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD));
            titlePara.setAlignment(Element.ALIGN_CENTER);
            document.add(titlePara);

            // Try to fetch account details via injected AccountDao (if available)
            Account acc = null;
            try {
                if (accountDao != null && accNo != null) {
                    acc = accountDao.findByAccountNumber(accNo);
                }
            } catch (Exception e) {
                // ignore lookup errors - fall back to minimal info
            }

            Font infoFont = new Font(Font.FontFamily.HELVETICA, 12);
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(80);
            infoTable.setSpacingAfter(8f);
            infoTable.setHorizontalAlignment(Element.ALIGN_LEFT);

            // Left label / Right value
            addInfoCell(infoTable, "Account Number", safe(accNo), infoFont);
            addInfoCell(infoTable, "Holder Name", safe(acc != null ? acc.getHolderName() : null), infoFont);
            addInfoCell(infoTable, "Branch", safe(acc != null ? acc.getBranchName() : null), infoFont);
            addInfoCell(infoTable, "IFSC Code", safe(acc != null ? acc.getIfscCode() : null), infoFont);
            addInfoCell(infoTable, "Bank Name", "AstroNova Bank", infoFont);

            document.add(infoTable);

            // Transactions table as before
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);

            addHeader(table,
                    new String[]{"Tx ID", "Date", "Type", "Amount", "From Acc", "To Acc", "Category", "Status"},
                    new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD)
            );

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 10);

            if (transactions != null) {
                for (TransactionRecord tx : transactions) {
                    table.addCell(new Phrase(safe(tx.getTxId()), bodyFont));
                    table.addCell(new Phrase(tx.getFormattedDate(), bodyFont));
                    table.addCell(new Phrase(prettyType(tx.getTxType()), bodyFont));
                    table.addCell(new Phrase("₹" + (tx.getAmount() != null ? tx.getAmount() : "0.00"), bodyFont));
                    table.addCell(new Phrase(safe(tx.getFromAccount()), bodyFont));
                    table.addCell(new Phrase(safe(tx.getToAccount()), bodyFont));
                    table.addCell(new Phrase(safe(tx.getCategory()), bodyFont));
                    table.addCell(new Phrase(safe(tx.getStatus()), bodyFont));
                }
            }

            document.add(table);
            document.add(new Paragraph("\nGenerated by AstroNova Bank © " + java.time.LocalDate.now()));
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    // ---------------------------------------------------
    // small helper to add a label/value pair to a 2-column table
    // ---------------------------------------------------
    private static void addInfoCell(PdfPTable t, String label, String value, Font font) {
        PdfPCell cellLabel = new PdfPCell(new Phrase(label, font));
        cellLabel.setBorder(Rectangle.NO_BORDER);
        t.addCell(cellLabel);

        PdfPCell cellVal = new PdfPCell(new Phrase(value == null ? "-" : value, font));
        cellVal.setBorder(Rectangle.NO_BORDER);
        t.addCell(cellVal);
    }

    // ---------------------------------------------------
    // ENCRYPTION
    // ---------------------------------------------------
    private static byte[] encryptIfNeeded(byte[] pdfBytes, String key) {
        if (key == null || key.trim().isEmpty())
            return pdfBytes;

        try {
            ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
            PdfReader reader = new PdfReader(pdfBytes);
            PdfStamper stamper = new PdfStamper(reader, encrypted);

            stamper.setEncryption(
                    key.getBytes(),
                    key.getBytes(),
                    PdfWriter.ALLOW_PRINTING,
                    PdfWriter.ENCRYPTION_AES_128
            );

            stamper.close();
            reader.close();
            return encrypted.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return pdfBytes;
        }
    }

    // ---------------------------------------------------
    // HELPERS
    // ---------------------------------------------------
    private static void addHeader(PdfPTable table, String[] headers, Font font) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }
}
