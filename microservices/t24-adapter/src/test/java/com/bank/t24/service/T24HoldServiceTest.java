package com.bank.t24.service;

import com.bank.t24.dto.T24HoldRequest;
import com.bank.t24.dto.T24HoldResponse;
import com.bank.t24.dto.T24ReleaseRequest;
import com.bank.t24.model.Account;
import com.bank.t24.model.LockedAmount;
import com.bank.t24.repository.AccountRepository;
import com.bank.t24.repository.LockedAmountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class T24HoldServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LockedAmountRepository lockedAmountRepository;

    private T24HoldService holdService;

    @BeforeEach
    void setUp() {
        holdService = new T24HoldService(accountRepository, lockedAmountRepository);
    }

    @Test
    @DisplayName("Successfully place hold when available balance is sufficient")
    void testPlaceHoldSuccess() {
        Account acc = new Account();
        acc.setAccountId(1L);
        acc.setAccountNumber("001181233469");
        acc.setCurrentBalance(new BigDecimal("10000.00"));
        acc.setHeldBalance(new BigDecimal("1000.00")); // avail = 9000
        acc.setStatus("ACTIVE");

        when(lockedAmountRepository.findByReferenceNo("REF-001")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acc));
        when(lockedAmountRepository.save(any(LockedAmount.class))).thenAnswer(inv -> {
            LockedAmount l = inv.getArgument(0);
            l.setHoldId(99L);
            return l;
        });

        T24HoldRequest req = new T24HoldRequest(1L, null, new BigDecimal("2000.00"), "PHP", "REF-001");
        T24HoldResponse res = holdService.placeHold(req);

        assertThat(res.getStatus()).isEqualTo(LockedAmount.STATUS_ACTIVE);
        assertThat(res.getAmount()).isEqualByComparingTo("2000.00");
        assertThat(acc.getHeldBalance()).isEqualByComparingTo("3000.00");
        assertThat(acc.getAvailableBalance()).isEqualByComparingTo("7000.00");
        verify(accountRepository).save(acc);
    }

    @Test
    @DisplayName("Idempotent hold returns existing hold without double holding")
    void testPlaceHoldIdempotency() {
        LockedAmount existing = new LockedAmount(1L, new BigDecimal("2000.00"), "PHP", "REF-001", null);
        existing.setHoldId(55L);
        Account acc = new Account();
        acc.setAccountId(1L);
        acc.setCurrentBalance(new BigDecimal("10000.00"));
        acc.setHeldBalance(new BigDecimal("2000.00"));

        when(lockedAmountRepository.findByReferenceNo("REF-001")).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(acc));

        T24HoldRequest req = new T24HoldRequest(1L, null, new BigDecimal("2000.00"), "PHP", "REF-001");
        T24HoldResponse res = holdService.placeHold(req);

        assertThat(res.getHoldId()).isEqualTo(55L);
        assertThat(res.getMessage()).contains("Idempotent");
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Place hold fails if available balance is insufficient")
    void testPlaceHoldInsufficientFunds() {
        Account acc = new Account();
        acc.setAccountId(1L);
        acc.setCurrentBalance(new BigDecimal("1000.00"));
        acc.setHeldBalance(new BigDecimal("500.00")); // avail = 500
        acc.setStatus("ACTIVE");

        when(lockedAmountRepository.findByReferenceNo("REF-002")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acc));

        T24HoldRequest req = new T24HoldRequest(1L, null, new BigDecimal("600.00"), "PHP", "REF-002");

        assertThatThrownBy(() -> holdService.placeHold(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");

        verify(accountRepository, never()).save(acc);
    }

    @Test
    @DisplayName("Place hold fails if account is FROZEN")
    void testPlaceHoldFrozenAccount() {
        Account acc = new Account();
        acc.setAccountId(1L);
        acc.setCurrentBalance(new BigDecimal("5000.00"));
        acc.setStatus("FROZEN");

        when(lockedAmountRepository.findByReferenceNo("REF-003")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acc));

        T24HoldRequest req = new T24HoldRequest(1L, null, new BigDecimal("100.00"), "PHP", "REF-003");

        assertThatThrownBy(() -> holdService.placeHold(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FROZEN");
    }

    @Test
    @DisplayName("Successfully release active hold")
    void testReleaseHoldSuccess() {
        LockedAmount hold = new LockedAmount(1L, new BigDecimal("1500.00"), "PHP", "REF-004", null);
        hold.setHoldId(77L);
        hold.setStatus(LockedAmount.STATUS_ACTIVE);

        Account acc = new Account();
        acc.setAccountId(1L);
        acc.setCurrentBalance(new BigDecimal("10000.00"));
        acc.setHeldBalance(new BigDecimal("1500.00"));

        when(lockedAmountRepository.findByReferenceNo("REF-004")).thenReturn(Optional.of(hold));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acc));

        T24ReleaseRequest req = new T24ReleaseRequest("REF-004");
        T24HoldResponse res = holdService.releaseHold(req);

        assertThat(res.getStatus()).isEqualTo(LockedAmount.STATUS_RELEASED);
        assertThat(acc.getHeldBalance()).isEqualByComparingTo("0.00");
        assertThat(acc.getAvailableBalance()).isEqualByComparingTo("10000.00");
        assertThat(hold.getStatus()).isEqualTo(LockedAmount.STATUS_RELEASED);
        verify(accountRepository).save(acc);
        verify(lockedAmountRepository).save(hold);
    }

    @Test
    @DisplayName("Release nonexistent hold is idempotently safe")
    void testReleaseHoldNonExistent() {
        when(lockedAmountRepository.findByReferenceNo("REF-UNKNOWN")).thenReturn(Optional.empty());

        T24ReleaseRequest req = new T24ReleaseRequest("REF-UNKNOWN");
        T24HoldResponse res = holdService.releaseHold(req);

        assertThat(res.getStatus()).isEqualTo(LockedAmount.STATUS_RELEASED);
        verify(accountRepository, never()).save(any());
    }
}
