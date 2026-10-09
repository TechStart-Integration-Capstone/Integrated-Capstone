package com.bank.t24.repository;

import com.bank.t24.model.Account;
import com.bank.t24.model.LockedAmount;
import com.bank.t24.model.PostingJournal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class T24CorePersistenceTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LockedAmountRepository lockedAmountRepository;

    @Autowired
    private PostingJournalRepository postingJournalRepository;

    @Test
    @DisplayName("Persist and query Account in T24 schema")
    void testAccountPersistence() {
        Account acc = new Account();
        acc.setCustomerId(101L);
        acc.setAccountNumber("001181233469");
        acc.setAccountType("SAVINGS");
        acc.setCurrentBalance(new BigDecimal("50000.0000"));
        acc.setHeldBalance(new BigDecimal("500.0000"));
        acc.setStatus("ACTIVE");

        Account saved = accountRepository.save(acc);
        assertThat(saved.getAccountId()).isNotNull();
        assertThat(saved.getAvailableBalance()).isEqualByComparingTo("49500.0000");

        Account found = accountRepository.findByAccountNumber("001181233469").orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getCustomerId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Persist and query LockedAmount in T24 schema")
    void testLockedAmountPersistence() {
        LockedAmount hold = new LockedAmount(
                1L,
                new BigDecimal("1500.0000"),
                "PHP",
                "HOLD-REF-001",
                LocalDateTime.now().plusHours(1)
        );

        LockedAmount saved = lockedAmountRepository.save(hold);
        assertThat(saved.getHoldId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(LockedAmount.STATUS_ACTIVE);

        LockedAmount found = lockedAmountRepository.findByReferenceNo("HOLD-REF-001").orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getAmount()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("Persist and query PostingJournal in T24 schema")
    void testPostingJournalPersistence() {
        PostingJournal journal = new PostingJournal(
                "FT-20261007-001",
                1L,
                2L,
                new BigDecimal("1000.0000"),
                "PHP",
                new BigDecimal("5000.0000"),
                new BigDecimal("4000.0000"),
                new BigDecimal("2000.0000"),
                new BigDecimal("3000.0000")
        );

        PostingJournal saved = postingJournalRepository.save(journal);
        assertThat(saved.getJournalId()).isNotNull();

        PostingJournal found = postingJournalRepository.findByReferenceNo("FT-20261007-001").orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getDebitBalanceAfter()).isEqualByComparingTo("4000.0000");
        assertThat(found.getCreditBalanceAfter()).isEqualByComparingTo("3000.0000");
    }
}
