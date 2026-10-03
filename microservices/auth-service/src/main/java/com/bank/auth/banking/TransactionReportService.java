package com.bank.auth.banking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TransactionReportService {
    static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private final BankingService banking;
    private final JdbcTemplate jdbc;
    public TransactionReportService(BankingService banking, JdbcTemplate jdbc) {
        this.banking = banking;
        this.jdbc = jdbc;
    }
    public record Row(LocalDateTime date, String reference, String type, String status, String operation, BigDecimal amount, String recipient) {}

    @Transactional(readOnly = true)
    public byte[] generate(String authorization, long accountId, LocalDate from, LocalDate to) throws IOException {
        var profile = banking.profile(authorization);
        var account = profile.accounts().stream().filter(a -> a.accountId() == accountId).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Choose one of your own accounts."));
        if (from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now(ZONE)) || ChronoUnit.DAYS.between(from, to) > 365)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid date range of up to 366 days, ending today or earlier.");
        var start = from.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var end = to.plusDays(1).atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var rows = jdbc.query("SELECT t.transaction_date, t.reference_no, t.transaction_type, t.status, "
            + "COALESCE((SELECT JSON_VALUE(o.payload, '$.operation') FROM OUTBOX_EVENT o WHERE o.transaction_id = t.transaction_id ORDER BY o.event_id OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY), "
            + "CASE WHEN t.transaction_type IN ('CREDIT','WELCOME_GIFT','TRANSFER_IN') THEN 'CREDIT' WHEN t.transaction_type IN ('DEBIT','TRANSFER_OUT') OR t.transaction_type LIKE 'EXT_%' THEN 'DEBIT' END), "
            + "t.amount, CONCAT(c.first_name, ' ', c.last_name), t.transaction_id FROM LEDGER_TRANSACTION t LEFT JOIN ACCOUNT a ON a.account_id = t.to_account_id LEFT JOIN CUSTOMER c ON c.customer_id = a.customer_id "
            + "WHERE t.from_account_id = ? AND t.transaction_date >= ? AND t.transaction_date < ? ORDER BY t.transaction_date, t.transaction_id OFFSET 0 ROWS FETCH NEXT 10001 ROWS ONLY",
            (rs, n) -> new Row(rs.getTimestamp(1).toLocalDateTime(), BankingIdentifiers.reference(rs.getLong(8), rs.getTimestamp(1).toLocalDateTime()), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getString(7)),
            accountId, Timestamp.valueOf(start), Timestamp.valueOf(end));
        if (rows.size() > 10000) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This period contains over 10,000 transactions. Choose a shorter date range.");
        return pdf(profile, account, from, to, rows);
    }

    static boolean completed(Row row) { return List.of("SUCCESS", "COMPLETED").contains(row.status()); }
    static String amount(BigDecimal value) { return String.format(Locale.US, "%,.2f", value); }

    static byte[] pdf(BankingService.Profile profile, BankingService.Account account, LocalDate from, LocalDate to, List<Row> rows) {
        BigDecimal incoming = BigDecimal.ZERO, outgoing = BigDecimal.ZERO;
        for (var row : rows) if (completed(row)) {
            if ("CREDIT".equals(row.operation())) incoming = incoming.add(row.amount());
            if ("DEBIT".equals(row.operation())) outgoing = outgoing.add(row.amount());
        }

        int pageSize = 9;
        int pages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        String generated = ZonedDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'PHT'", Locale.ENGLISH));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Long> objOffsets = new ArrayList<>();

        try {
            out.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));

            int font1Obj = 3 + pages * 2;
            int font2Obj = font1Obj + 1;

            // 1. Catalog
            objOffsets.add((long) out.size());
            out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            // 2. Pages
            objOffsets.add((long) out.size());
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                kids.append((3 + i * 2)).append(" 0 R ");
            }
            out.write(String.format(Locale.US, "2 0 obj\n<< /Type /Pages /Kids [%s] /Count %d >>\nendobj\n", kids.toString().trim(), pages).getBytes(StandardCharsets.ISO_8859_1));

            for (int pageNumber = 0; pageNumber < pages; pageNumber++) {
                int pageObj = 3 + pageNumber * 2;
                int streamObj = pageObj + 1;

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                stream.write("0.396 0.110 0.243 rg 0 523 842 72 re f\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("1 1 1 rg BT /F2 25 Tf 36 552 Td (PayPink) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("1 1 1 rg BT /F1 13 Tf 535 552 Td (TRANSACTION REPORT) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));

                stream.write(String.format(Locale.US, "0.16 0.14 0.16 rg BT /F2 12 Tf 36 501 Td (%s) Tj ET\n", escapePdf(profile.fullName())).getBytes(StandardCharsets.ISO_8859_1));
                String ending = account.accountNumber().substring(Math.max(0, account.accountNumber().length() - 4));
                String accMeta = account.accountType().replace('_', ' ') + " | Account ending " + ending + " | " + account.currency();
                stream.write(String.format(Locale.US, "BT /F1 10 Tf 36 482 Td (%s) Tj ET\n", escapePdf(accMeta)).getBytes(StandardCharsets.ISO_8859_1));
                stream.write(String.format(Locale.US, "BT /F1 10 Tf 36 464 Td (Period: %s to %s \\(inclusive, Philippine time\\)) Tj ET\n", from, to).getBytes(StandardCharsets.ISO_8859_1));
                stream.write(String.format(Locale.US, "BT /F1 9 Tf 535 501 Td (Generated: %s) Tj ET\n", escapePdf(generated)).getBytes(StandardCharsets.ISO_8859_1));

                stream.write(String.format(Locale.US, "BT /F1 10 Tf 36 441 Td (Completed money in: %s %s) Tj ET\n", account.currency(), amount(incoming)).getBytes(StandardCharsets.ISO_8859_1));
                stream.write(String.format(Locale.US, "BT /F1 10 Tf 300 441 Td (Completed money out: %s %s) Tj ET\n", account.currency(), amount(outgoing)).getBytes(StandardCharsets.ISO_8859_1));
                stream.write(String.format(Locale.US, "BT /F1 10 Tf 620 441 Td (%d transactions) Tj ET\n", rows.size()).getBytes(StandardCharsets.ISO_8859_1));

                stream.write("0.96 0.92 0.94 rg 36 409 770 22 re f\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("0.16 0.14 0.16 rg\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("BT /F2 9 Tf 42 416 Td (Date / time \\(PHT\\)) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("BT /F2 9 Tf 165 416 Td (Transaction / reference) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("BT /F2 9 Tf 490 416 Td (Status) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("BT /F2 9 Tf 592 416 Td (Money out) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("BT /F2 9 Tf 709 416 Td (Money in) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));

                if (rows.isEmpty()) {
                    stream.write("BT /F1 12 Tf 42 380 Td (No transactions occurred in this date range.) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                }

                int last = Math.min(rows.size(), (pageNumber + 1) * pageSize);
                for (int i = pageNumber * pageSize; i < last; i++) {
                    var row = rows.get(i);
                    float y = 391 - (i % pageSize) * 34;
                    String date = row.date().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH));
                    String type = row.type().startsWith("EXT_") ? (row.type().contains("PESONET") ? "PESONet transfer" : "InstaPay transfer") : row.type().replace('_', ' ');
                    var recipient = ExternalTransferService.recipientForType(row.type());
                    String name = recipient == null ? row.recipient() : recipient.name();
                    if (name != null && !name.isBlank()) type += " - " + name.strip();

                    stream.write(String.format(Locale.US, "BT /F1 8 Tf 42 %.1f Td (%s) Tj ET\n", y, escapePdf(date)).getBytes(StandardCharsets.ISO_8859_1));
                    stream.write(String.format(Locale.US, "BT /F1 9 Tf 165 %.1f Td (%s) Tj ET\n", y, escapePdf(type)).getBytes(StandardCharsets.ISO_8859_1));
                    stream.write(String.format(Locale.US, "BT /F1 8 Tf 490 %.1f Td (%s) Tj ET\n", y, completed(row) ? "Completed" : row.status()).getBytes(StandardCharsets.ISO_8859_1));
                    stream.write(String.format(Locale.US, "BT /F1 9 Tf 592 %.1f Td (%s) Tj ET\n", y, "DEBIT".equals(row.operation()) ? amount(row.amount()) : "-").getBytes(StandardCharsets.ISO_8859_1));
                    stream.write(String.format(Locale.US, "BT /F1 9 Tf 709 %.1f Td (%s) Tj ET\n", y, "CREDIT".equals(row.operation()) ? amount(row.amount()) : "-").getBytes(StandardCharsets.ISO_8859_1));
                    stream.write(String.format(Locale.US, "BT /F1 7 Tf 165 %.1f Td (%s) Tj ET\n", y - 12, escapePdf(row.reference())).getBytes(StandardCharsets.ISO_8859_1));

                    stream.write(String.format(Locale.US, "0.9 0.88 0.89 RG 36 %.1f m 806 %.1f l S\n", y - 19, y - 19).getBytes(StandardCharsets.ISO_8859_1));
                }

                stream.write("BT /F1 8 Tf 36 65 Td (Pending and failed amounts are shown for reference and excluded from completed totals.) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("BT /F1 8 Tf 36 51 Td (Dates reflect transaction submission. Statuses reflect the time this report was generated. Amounts shown to two decimals.) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write("BT /F1 8 Tf 36 30 Td (PayPink | Personal banking | Transaction report) Tj ET\n".getBytes(StandardCharsets.ISO_8859_1));
                stream.write(String.format(Locale.US, "BT /F1 8 Tf 726 30 Td (Page %d of %d) Tj ET\n", pageNumber + 1, pages).getBytes(StandardCharsets.ISO_8859_1));

                byte[] streamBytes = stream.toByteArray();

                objOffsets.add((long) out.size());
                out.write(String.format(Locale.US, "%d 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 %d 0 R /F2 %d 0 R >> >> /Contents %d 0 R >>\nendobj\n",
                    pageObj, font1Obj, font2Obj, streamObj).getBytes(StandardCharsets.ISO_8859_1));

                objOffsets.add((long) out.size());
                out.write(String.format(Locale.US, "%d 0 obj\n<< /Length %d >>\nstream\n", streamObj, streamBytes.length).getBytes(StandardCharsets.ISO_8859_1));
                out.write(streamBytes);
                out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
            }

            objOffsets.add((long) out.size());
            out.write(String.format(Locale.US, "%d 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n", font1Obj).getBytes(StandardCharsets.ISO_8859_1));

            objOffsets.add((long) out.size());
            out.write(String.format(Locale.US, "%d 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n", font2Obj).getBytes(StandardCharsets.ISO_8859_1));

            long xrefOffset = out.size();
            int totalObjs = font2Obj;
            out.write(String.format(Locale.US, "xref\n0 %d\n0000000000 65535 f \n", totalObjs + 1).getBytes(StandardCharsets.ISO_8859_1));
            for (Long off : objOffsets) {
                out.write(String.format(Locale.US, "%010d 00000 n \n", off).getBytes(StandardCharsets.ISO_8859_1));
            }

            out.write(String.format(Locale.US, "trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n", totalObjs + 1, xrefOffset).getBytes(StandardCharsets.ISO_8859_1));

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private static String escapePdf(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
