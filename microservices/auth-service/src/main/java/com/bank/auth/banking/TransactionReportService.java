package com.bank.auth.banking;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TransactionReportService {
    static final ZoneId ZONE=ZoneId.of("Asia/Manila");
    private final BankingService banking;
    private final JdbcTemplate jdbc;
    public TransactionReportService(BankingService banking,JdbcTemplate jdbc) { this.banking=banking;this.jdbc=jdbc; }
    public record Row(LocalDateTime date,String reference,String type,String status,String operation,BigDecimal amount,String recipient) {}
    @Transactional(readOnly=true)
    public byte[] generate(String authorization,long accountId,LocalDate from,LocalDate to) throws IOException {
        var profile=banking.profile(authorization);
        var account=profile.accounts().stream().filter(a->a.accountId()==accountId).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Choose one of your own accounts."));
        if (from==null || to==null || from.isAfter(to) || to.isAfter(LocalDate.now(ZONE)) || ChronoUnit.DAYS.between(from,to)>365)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid date range of up to 366 days, ending today or earlier.");
        var start=from.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var end=to.plusDays(1).atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var rows=jdbc.query("SELECT t.transaction_date,t.reference_no,t.transaction_type,t.status,"
            +"COALESCE((SELECT JSON_VALUE(o.payload,'$.operation') FROM OUTBOX_EVENT o WHERE o.transaction_id=t.transaction_id ORDER BY o.event_id FETCH FIRST 1 ROW ONLY),"
            +"CASE WHEN t.transaction_type IN ('CREDIT','WELCOME_GIFT','TRANSFER_IN') THEN 'CREDIT' WHEN t.transaction_type IN ('DEBIT','TRANSFER_OUT') OR t.transaction_type LIKE 'EXT_%' THEN 'DEBIT' END),"
            +"t.amount,c.first_name || ' ' || c.last_name,t.transaction_id FROM TRANSACTION t LEFT JOIN ACCOUNT a ON a.account_id=t.to_account_id LEFT JOIN CUSTOMER c ON c.customer_id=a.customer_id "
            +"WHERE t.from_account_id=? AND t.transaction_date>=? AND t.transaction_date<? ORDER BY t.transaction_date,t.transaction_id FETCH FIRST 10001 ROWS ONLY",
            (rs,n)->new Row(rs.getTimestamp(1).toLocalDateTime(),BankingIdentifiers.reference(rs.getLong(8),rs.getTimestamp(1).toLocalDateTime()),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBigDecimal(6),rs.getString(7)),
            accountId,Timestamp.valueOf(start),Timestamp.valueOf(end));
        if(rows.size()>10000) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"This period contains over 10,000 transactions. Choose a shorter date range.");
        return pdf(profile,account,from,to,rows);
    }
    static boolean completed(Row row) { return List.of("SUCCESS","COMPLETED").contains(row.status()); }
    static String amount(BigDecimal value) { return String.format(Locale.US,"%,.2f",value); }
    static byte[] pdf(BankingService.Profile profile,BankingService.Account account,LocalDate from,LocalDate to,List<Row> rows) throws IOException {
        BigDecimal incoming=BigDecimal.ZERO,outgoing=BigDecimal.ZERO;
        for(var row:rows) if(completed(row)) {
            if("CREDIT".equals(row.operation())) incoming=incoming.add(row.amount());
            if("DEBIT".equals(row.operation())) outgoing=outgoing.add(row.amount());
        }
        try(var doc=new PDDocument(); var output=new ByteArrayOutputStream()) {
            var regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            int pageSize=9, pages=Math.max(1,(rows.size()+pageSize-1)/pageSize);
            String generated=ZonedDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'PHT'",Locale.ENGLISH));
            for(int pageNumber=0;pageNumber<pages;pageNumber++) {
                var page=new PDPage(new PDRectangle(842,595));doc.addPage(page);
                try(var canvas=new PDPageContentStream(doc,page)) {
                    canvas.setNonStrokingColor(101/255f,28/255f,62/255f);canvas.addRect(0,523,842,72);canvas.fill();
                    canvas.setNonStrokingColor(1f,1f,1f);text(canvas,bold,25,36,552,"PayPink");text(canvas,regular,13,535,552,"TRANSACTION REPORT");
                    canvas.setNonStrokingColor(.16f,.14f,.16f);
                    text(canvas,bold,12,36,501,profile.fullName());
                    text(canvas,regular,10,36,482,account.accountType().replace('_',' ')+" | Account ending "+account.accountNumber().substring(Math.max(0,account.accountNumber().length()-4))+" | "+account.currency());
                    text(canvas,regular,10,36,464,"Period: "+from+" to "+to+" (inclusive, Philippine time)");
                    text(canvas,regular,9,535,501,"Generated: "+generated);
                    text(canvas,regular,10,36,441,"Completed money in: "+account.currency()+" "+amount(incoming));
                    text(canvas,regular,10,300,441,"Completed money out: "+account.currency()+" "+amount(outgoing));
                    text(canvas,regular,10,620,441,rows.size()+" transactions");
                    canvas.setNonStrokingColor(.96f,.92f,.94f);canvas.addRect(36,409,770,22);canvas.fill();canvas.setNonStrokingColor(.16f,.14f,.16f);
                    text(canvas,bold,9,42,416,"Date / time (PHT)");text(canvas,bold,9,165,416,"Transaction / reference");text(canvas,bold,9,490,416,"Status");text(canvas,bold,9,592,416,"Money out");text(canvas,bold,9,709,416,"Money in");
                    if(rows.isEmpty()) text(canvas,regular,12,42,380,"No transactions occurred in this date range.");
                    int last=Math.min(rows.size(),(pageNumber+1)*pageSize);
                    for(int i=pageNumber*pageSize;i<last;i++) {
                        var row=rows.get(i);float y=391-(i%pageSize)*34;
                        String date=row.date().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm",Locale.ENGLISH));
                        String type=row.type().startsWith("EXT_")?(row.type().contains("PESONET")?"PESONet transfer":"InstaPay transfer"):row.type().replace('_',' ');
                        var recipient=ExternalTransferService.recipientForType(row.type());String name=recipient==null?row.recipient():recipient.name();
                        if(name!=null && !name.isBlank()) type+=" - "+name.strip();
                        text(canvas,regular,8,42,y,date);text(canvas,regular,9,165,y,fit(regular,type,9,310));
                        text(canvas,regular,8,490,y,completed(row)?"Completed":row.status());
                        text(canvas,regular,9,592,y,"DEBIT".equals(row.operation())?amount(row.amount()):"-");
                        text(canvas,regular,9,709,y,"CREDIT".equals(row.operation())?amount(row.amount()):"-");
                        text(canvas,regular,7,165,y-12,row.reference());
                        canvas.setStrokingColor(.9f,.88f,.89f);canvas.moveTo(36,y-19);canvas.lineTo(806,y-19);canvas.stroke();
                    }
                    text(canvas,regular,8,36,65,"Pending and failed amounts are shown for reference and excluded from completed totals.");
                    text(canvas,regular,8,36,51,"Dates reflect transaction submission. Statuses reflect the time this report was generated. Amounts shown to two decimals.");
                    text(canvas,regular,8,36,30,"PayPink | Personal banking | Transaction report");text(canvas,regular,8,726,30,"Page "+(pageNumber+1)+" of "+pages);
                }
            }
            doc.getDocumentInformation().setTitle("PayPink Transaction Report "+from+" to "+to);
            doc.getDocumentInformation().setAuthor("PayPink");doc.save(output);return output.toByteArray();
        }
    }
    static String safe(PDFont font,String value) {
        var result=new StringBuilder();
        for(int point:Objects.requireNonNullElse(value,"").codePoints().toArray()) {
            String c=new String(Character.toChars(point));
            try { font.encode(c);result.append(c); } catch(Exception ex) { result.append('?'); }
        }
        return result.toString();
    }
    static String fit(PDFont font,String value,float size,float width) throws IOException {
        String s=safe(font,value);if(font.getStringWidth(s)*size/1000<=width)return s;
        while(!s.isEmpty() && font.getStringWidth(s+"...")*size/1000>width)s=s.substring(0,s.length()-1);
        return s+"...";
    }
    static void text(PDPageContentStream canvas,PDFont font,float size,float x,float y,String value) throws IOException {
        canvas.beginText();canvas.setFont(font,size);canvas.newLineAtOffset(x,y);canvas.showText(safe(font,value));canvas.endText();
    }
}
