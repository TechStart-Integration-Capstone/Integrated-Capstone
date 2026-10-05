package com.bank.auth.banking;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** Customer-facing identifiers. Internal ledger references remain immutable for replay/audit. */
public final class BankingIdentifiers {
    private BankingIdentifiers() {}
    private static final SecureRandom RANDOM = new SecureRandom();
    public static String typeCode(String type) {
        return switch (type) {
            case "SAVINGS", "SAVINGS_ACCOUNT" -> "1";
            case "EVERYDAY_ACCOUNT" -> "2";
            case "CHECKING_ACCOUNT" -> "3";
            case "TIME_DEPOSIT" -> "4";
            case "STRESS_TEST_ACCOUNT" -> "9";
            default -> throw new IllegalArgumentException("Unsupported account type: " + type);
        };
    }
    public static String account(String type, String customerNumber) {
        if(customerNumber == null || !customerNumber.matches("[0-9]{7}"))
            throw new IllegalArgumentException("Customer number must contain seven digits");
        String body = "001" + typeCode(type) + customerNumber;
        int sum = 0;
        for(int i = 0; i < body.length(); i++) {
            int digit = body.charAt(i) - '0';
            if(i % 2 == 0) { digit *= 2; if(digit > 9) digit -= 9; }
            sum += digit;
        }
        return body + ((10 - sum % 10) % 10);
    }
    public static boolean isAccount(String number) {
        if(number == null || !number.matches("001[12349][0-9]{8}")) return false;
        int sum = 0;
        for(int i = 0; i < number.length(); i++) {
            int digit = number.charAt(i) - '0';
            if(i % 2 == 0) { digit *= 2; if(digit > 9) digit -= 9; }
            sum += digit;
        }
        return sum % 10 == 0;
    }
    public static String newCustomerNumber(Predicate<String> reserved) {
        return newCustomerNumber(() -> RANDOM.nextInt(10_000_000), reserved);
    }
    static String newCustomerNumber(IntSupplier random, Predicate<String> reserved) {
        for(int attempt = 0; attempt < 128; attempt++) {
            String candidate = String.format(Locale.ROOT, "%07d", random.getAsInt());
            if(!reserved.test(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to allocate a unique customer number");
    }
    public static String reference(long id,LocalDateTime date) {
        return "PP-"+date.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Manila"))
            .toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format(Locale.ROOT,"%012d",id);
    }
    public static String resolveAccount(org.springframework.jdbc.core.JdbcTemplate jdbc,String number) {
        String normalized=number.replaceAll("\\s","").toUpperCase(Locale.ROOT);
        var aliases=jdbc.queryForList("SELECT a.account_number FROM ACCOUNT a JOIN AUDIT_LOG l ON l.entity = 'ACCOUNT:' + CAST(a.account_id AS NVARCHAR(30)) WHERE l.action='ACCOUNT_RENUMBERED' AND l.details=?",String.class,normalized);
        return aliases.isEmpty()?normalized:aliases.get(0);
    }
}
