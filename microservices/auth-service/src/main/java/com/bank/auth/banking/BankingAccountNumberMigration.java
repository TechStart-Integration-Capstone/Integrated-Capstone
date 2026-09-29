package com.bank.auth.banking;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Existing balances, IDs and relationships are retained; old numbers remain aliases. */
@Component
public class BankingAccountNumberMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public BankingAccountNumberMigration(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override @Transactional
    public void run(ApplicationArguments args) {
        var accounts=jdbc.query("SELECT account_id,account_number,customer_id,account_type FROM ACCOUNT ORDER BY account_id FOR UPDATE",
                (rs,row) -> new Existing(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getString(4)));
        Set<String> reserved = new HashSet<>();
        Map<Long,String> customerNumbers = new HashMap<>();
        Map<String,Long> owners = new HashMap<>();
        Set<String> customerTypes = new HashSet<>();
        for(var account:accounts) {
            if(!customerTypes.add(account.customerId()+":"+BankingIdentifiers.typeCode(account.type())))
                throw new IllegalStateException("Multiple accounts with the same type code for customer " + account.customerId());
            reserve(reserved,account.number());
            if(BankingIdentifiers.isAccount(account.number())) {
                String token = account.number().substring(4,11);
                String previous = customerNumbers.putIfAbsent(account.customerId(),token);
                Long owner = owners.putIfAbsent(token,account.customerId());
                if((previous != null && !previous.equals(token)) || (owner != null && owner != account.customerId()))
                    throw new IllegalStateException("Inconsistent customer account numbers");
            }
        }
        jdbc.queryForList("SELECT details FROM AUDIT_LOG WHERE action='ACCOUNT_RENUMBERED'",String.class)
                .forEach(number -> reserve(reserved,number));
        // Plan every replacement first. A restart preserves already valid numbers and adds no duplicate aliases.
        Map<Long,String> replacements = new LinkedHashMap<>();
        for(var account:accounts) {
            String token = customerNumbers.computeIfAbsent(account.customerId(),id -> {
                String generated = BankingIdentifiers.newCustomerNumber(reserved::contains);
                reserved.add(generated);
                return generated;
            });
            replacements.put(account.id(),BankingIdentifiers.account(account.type(),token));
        }
        for(var account:accounts) {
            String replacement = replacements.get(account.id());
            if(replacement.equals(account.number())) continue;
            jdbc.update("INSERT INTO AUDIT_LOG(customer_id,action,entity,details) VALUES (?,'ACCOUNT_RENUMBERED',?,?)",
                    account.customerId(),"ACCOUNT:"+account.id(),account.number());
            jdbc.update("UPDATE ACCOUNT SET account_number=? WHERE account_id=?",replacement,account.id());
        }
    }
    private static void reserve(Set<String> reserved,String number) {
        if(number != null && number.matches("[0-9]{12}")) reserved.add(number.substring(4,11));
    }
    private record Existing(long id,String number,long customerId,String type) {}
}
