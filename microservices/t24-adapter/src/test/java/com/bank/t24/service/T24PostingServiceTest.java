package com.bank.t24.service;

import com.bank.t24.model.Account;
import com.bank.t24.model.LockedAmount;
import com.bank.t24.model.PostingJournal;
import com.bank.t24.repository.AccountRepository;
import com.bank.t24.repository.LockedAmountRepository;
import com.bank.t24.repository.PostingJournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class T24PostingServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LockedAmountRepository lockedAmountRepository;

    @Mock
    private PostingJournalRepository postingJournalRepository;

    private T24PostingService postingService;

    @BeforeEach
    void setUp() {
        postingService = new T24PostingService(accountRepository, lockedAmountRepository, postingJournalRepository);
    }

    @Test
    @DisplayName("Successfully execute double-entry posting when hold exists")
    void testExecutePostingWithHoldSuccess() {
        Account debit = new Account();
        debit.setAccountId(1L);
        debit.setAccountNumber("001181233469");
        debit.setCurrentBalance(new BigDecimal("10000.00"));
        debit.setHeldBalance(new BigDecimal("1500.00"));
        debit.setStatus("ACTIVE");

        Account credit = new Account();
        credit.setAccountId(2L);
        credit.setAccountNumber("001181233470");
        credit.setCurrentBalance(new BigDecimal("5000.00"));
        credit.setHeldBalance(BigDecimal.ZERO);
        credit.setStatus("ACTIVE");

        LockedAmount hold = new LockedAmount(1L, new BigDecimal("1500.00"), "PHP", "REF-POST-1", null);
        hold.setStatus(LockedAmount.STATUS_ACTIVE);

        when(postingJournalRepository.findByReferenceNo("REF-POST-1")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumberForUpdate("001181233469")).thenReturn(Optional.of(debit));
        when(accountRepository.findByAccountNumberForUpdate("001181233470")).thenReturn(Optional.of(credit));
        when(lockedAmountRepository.findByReferenceNo("REF-POST-1")).thenReturn(Optional.of(hold));
        when(postingJournalRepository.save(any(PostingJournal.class))).thenAnswer(i -> i.getArgument(0));

        T24PostingService.PostingResult result = postingService.executeDoubleEntryPosting(
                "REF-POST-1", "001181233469", "001181233470", new BigDecimal("1500.00"), "PHP", "FT26001"
        );

        assertThat(result.success()).isTrue();
        assertThat(hold.getStatus()).isEqualTo(LockedAmount.STATUS_SETTLED);
        assertThat(debit.getCurrentBalance()).isEqualByComparingTo("8500.00");
        assertThat(debit.getHeldBalance()).isEqualByComparingTo("0.00");
        assertThat(credit.getCurrentBalance()).isEqualByComparingTo("6500.00");
        assertThat(result.journal()).isNotNull();
        assertThat(result.journal().getDebitBalanceAfter()).isEqualByComparingTo("8500.00");
        assertThat(result.journal().getCreditBalanceAfter()).isEqualByComparingTo("6500.00");

        verify(accountRepository).save(debit);
        verify(accountRepository).save(credit);
        verify(lockedAmountRepository).save(hold);
        verify(postingJournalRepository).save(any(PostingJournal.class));
    }

    @Test
    @DisplayName("Idempotent hit returns existing journal without re-posting")
    void testExecutePostingIdempotency() {
        PostingJournal existing = new PostingJournal("REF-POST-1", 1L, 2L, new BigDecimal("1500.00"), "PHP",
                new BigDecimal("10000.00"), new BigDecimal("8500.00"), new BigDecimal("5000.00"), new BigDecimal("6500.00"));

        when(postingJournalRepository.findByReferenceNo("REF-POST-1")).thenReturn(Optional.of(existing));

        T24PostingService.PostingResult result = postingService.executeDoubleEntryPosting(
                "REF-POST-1", "001181233469", "001181233470", new BigDecimal("1500.00"), "PHP", "FT26001"
        );

        assertThat(result.success()).isTrue();
        assertThat(result.journal()).isEqualTo(existing);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fails if debit account is FROZEN")
    void testExecutePostingFrozenAccount() {
        Account debit = new Account();
        debit.setAccountNumber("001181233469");
        debit.setStatus("FROZEN");

        Account credit = new Account();
        credit.setAccountNumber("001181233470");
        credit.setStatus("ACTIVE");

        when(postingJournalRepository.findByReferenceNo("REF-POST-2")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumberForUpdate("001181233469")).thenReturn(Optional.of(debit));
        when(accountRepository.findByAccountNumberForUpdate("001181233470")).thenReturn(Optional.of(credit));

        T24PostingService.PostingResult result = postingService.executeDoubleEntryPosting(
                "REF-POST-2", "001181233469", "001181233470", new BigDecimal("100.00"), "PHP", "FT26002"
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("FROZEN");
    }
}
