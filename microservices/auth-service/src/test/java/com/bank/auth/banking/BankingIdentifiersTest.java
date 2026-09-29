package com.bank.auth.banking;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class BankingIdentifiersTest {
    @Test void accountNumbersAreTwelveDigitsAndUnique() {
        assertThat(BankingIdentifiers.account("SAVINGS_ACCOUNT","1234567")).isEqualTo("001112345671");
        assertThat(BankingIdentifiers.account("EVERYDAY_ACCOUNT","1234567")).isEqualTo("001212345670");
        assertThat(BankingIdentifiers.account("SAVINGS_ACCOUNT","0000000")).isEqualTo("001100000007");
        assertThatThrownBy(()->BankingIdentifiers.account("SAVINGS_ACCOUNT","123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BankingIdentifiers.account("UNKNOWN","1234567")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void checksumRejectsEverySingleDigitChange() {
        String number=BankingIdentifiers.account("SAVINGS_ACCOUNT","1234567");
        assertThat(BankingIdentifiers.isAccount(number)).isTrue();
        for(int i=0;i<number.length();i++) for(char digit='0';digit<='9';digit++) {
            if(number.charAt(i)==digit) continue;
            assertThat(BankingIdentifiers.isAccount(number.substring(0,i)+digit+number.substring(i+1))).isFalse();
        }
        assertThat(BankingIdentifiers.isAccount("100000000001")).isFalse();
    }
    @Test void randomCustomerAllocationRetriesCollisionsAndPreservesLeadingZeros() {
        var draws = new java.util.concurrent.atomic.AtomicInteger(41);
        assertThat(BankingIdentifiers.newCustomerNumber(draws::getAndIncrement,"0000041"::equals)).isEqualTo("0000042");
        assertThatThrownBy(()->BankingIdentifiers.newCustomerNumber(()->41,number->true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void transactionReferencesUsePhilippineDateAndStableTransactionId() {
        assertThat(BankingIdentifiers.reference(15,LocalDateTime.of(2026,9,28,18,0))).isEqualTo("PP-20260929-000000000015");
        assertThat(BankingIdentifiers.reference(16,LocalDateTime.of(2026,9,28,18,0))).isEqualTo("PP-20260929-000000000016");
    }
}
