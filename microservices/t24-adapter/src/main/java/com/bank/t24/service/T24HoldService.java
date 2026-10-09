package com.bank.t24.service;

import com.bank.t24.dto.T24HoldRequest;
import com.bank.t24.dto.T24HoldResponse;
import com.bank.t24.dto.T24ReleaseRequest;
import com.bank.t24.model.Account;
import com.bank.t24.model.LockedAmount;
import com.bank.t24.repository.AccountRepository;
import com.bank.t24.repository.LockedAmountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class T24HoldService {

    private static final Logger log = LoggerFactory.getLogger(T24HoldService.class);

    private final AccountRepository accountRepository;
    private final LockedAmountRepository lockedAmountRepository;

    public T24HoldService(AccountRepository accountRepository, LockedAmountRepository lockedAmountRepository) {
        this.accountRepository = accountRepository;
        this.lockedAmountRepository = lockedAmountRepository;
    }

    @Transactional
    public T24HoldResponse placeHold(T24HoldRequest request) {
        String ref = request.getReferenceNo();

        // 1. Idempotency check: if hold already recorded for this reference, return it
        Optional<LockedAmount> existingHold = lockedAmountRepository.findByReferenceNo(ref);
        if (existingHold.isPresent()) {
            LockedAmount hold = existingHold.get();
            log.info("[t24-hold] Replaying existing hold for ref={} status={}", ref, hold.getStatus());
            Account acc = accountRepository.findById(hold.getAccountId()).orElse(null);
            BigDecimal avail = acc != null ? acc.getAvailableBalance() : BigDecimal.ZERO;
            return new T24HoldResponse(
                    hold.getHoldId(),
                    hold.getAccountId(),
                    acc != null ? acc.getAccountNumber() : null,
                    hold.getAmount(),
                    hold.getCurrency(),
                    hold.getReferenceNo(),
                    hold.getStatus(),
                    avail,
                    hold.getCreatedAt(),
                    "Hold already recorded (Idempotent response)"
            );
        }

        // 2. Locate account with pessimistic lock
        Account account;
        if (request.getAccountId() != null) {
            account = accountRepository.findByIdForUpdate(request.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + request.getAccountId()));
        } else if (request.getAccountNumber() != null && !request.getAccountNumber().isBlank()) {
            account = accountRepository.findByAccountNumberForUpdate(request.getAccountNumber().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found with number: " + request.getAccountNumber()));
        } else {
            throw new IllegalArgumentException("Either accountId or accountNumber must be provided");
        }

        // 3. Verify Account lifecycle state
        String accStatus = account.getStatus();
        if ("FROZEN".equalsIgnoreCase(accStatus)) {
            throw new IllegalStateException("Cannot place hold: Account " + account.getAccountNumber() + " is FROZEN");
        }
        if ("CLOSED".equalsIgnoreCase(accStatus)) {
            throw new IllegalStateException("Cannot place hold: Account " + account.getAccountNumber() + " is CLOSED");
        }
        if (!"ACTIVE".equalsIgnoreCase(accStatus)) {
            throw new IllegalStateException("Cannot place hold: Account " + account.getAccountNumber() + " is not ACTIVE (" + accStatus + ")");
        }

        // 4. Verify available funds
        accountRepository.protectReservations(account);
        BigDecimal available = account.getAvailableBalance();
        if (available.compareTo(request.getAmount()) < 0) {
            log.warn("[t24-hold] Insufficient funds for account={} available={} requested={}",
                    account.getAccountNumber(), available, request.getAmount());
            throw new IllegalStateException("Insufficient funds: available=" + available + " requested=" + request.getAmount());
        }

        // 5. Update Account held balance
        account.setHeldBalance(account.getHeldBalance().add(request.getAmount()));
        accountRepository.save(account);

        // 6. Record LockedAmount row
        LockedAmount hold = new LockedAmount(
                account.getAccountId(),
                request.getAmount(),
                request.getCurrency(),
                ref,
                LocalDateTime.now().plusHours(24)
        );
        LockedAmount savedHold = lockedAmountRepository.save(hold);

        log.info("[t24-hold] Successfully placed hold id={} ref={} account={} amount={} availAfter={}",
                savedHold.getHoldId(), ref, account.getAccountNumber(), request.getAmount(), account.getAvailableBalance());

        return new T24HoldResponse(
                savedHold.getHoldId(),
                account.getAccountId(),
                account.getAccountNumber(),
                savedHold.getAmount(),
                savedHold.getCurrency(),
                savedHold.getReferenceNo(),
                savedHold.getStatus(),
                account.getAvailableBalance(),
                savedHold.getCreatedAt(),
                "Hold placed successfully"
        );
    }

    @Transactional
    public T24HoldResponse releaseHold(T24ReleaseRequest request) {
        String ref = request.getReferenceNo();
        Optional<LockedAmount> existingHold = lockedAmountRepository.findByReferenceNo(ref);

        if (existingHold.isEmpty()) {
            log.info("[t24-hold] Hold reference {} not found, treating as already released (Idempotent)", ref);
            return new T24HoldResponse(
                    null, null, null, BigDecimal.ZERO, "PHP", ref,
                    LockedAmount.STATUS_RELEASED, BigDecimal.ZERO, LocalDateTime.now(),
                    "Hold reference not found; treated as released"
            );
        }

        LockedAmount hold = existingHold.get();
        if (!LockedAmount.STATUS_ACTIVE.equalsIgnoreCase(hold.getStatus())) {
            log.info("[t24-hold] Hold reference {} already in status {}, skipping release", ref, hold.getStatus());
            Account acc = accountRepository.findById(hold.getAccountId()).orElse(null);
            return new T24HoldResponse(
                    hold.getHoldId(),
                    hold.getAccountId(),
                    acc != null ? acc.getAccountNumber() : null,
                    hold.getAmount(),
                    hold.getCurrency(),
                    hold.getReferenceNo(),
                    hold.getStatus(),
                    acc != null ? acc.getAvailableBalance() : BigDecimal.ZERO,
                    hold.getCreatedAt(),
                    "Hold is already " + hold.getStatus()
            );
        }

        // Update Account held balance
        Account account = accountRepository.findByIdForUpdate(hold.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + hold.getAccountId()));

        accountRepository.protectReservations(account);
        BigDecimal currentHeld = account.getHeldBalance();
        BigDecimal newHeld = currentHeld.subtract(hold.getAmount());
        if (newHeld.compareTo(BigDecimal.ZERO) < 0) {
            newHeld = BigDecimal.ZERO;
        }
        account.setHeldBalance(newHeld);
        accountRepository.save(account);

        // Update hold status
        hold.setStatus(LockedAmount.STATUS_RELEASED);
        lockedAmountRepository.save(hold);

        log.info("[t24-hold] Successfully released hold ref={} account={} amount={} availAfter={}",
                ref, account.getAccountNumber(), hold.getAmount(), account.getAvailableBalance());

        return new T24HoldResponse(
                hold.getHoldId(),
                account.getAccountId(),
                account.getAccountNumber(),
                hold.getAmount(),
                hold.getCurrency(),
                hold.getReferenceNo(),
                hold.getStatus(),
                account.getAvailableBalance(),
                hold.getCreatedAt(),
                "Hold released successfully"
        );
    }

    @Transactional(readOnly = true)
    public Optional<T24HoldResponse> getHold(String referenceNo) {
        return lockedAmountRepository.findByReferenceNo(referenceNo).map(hold -> {
            Account acc = accountRepository.findById(hold.getAccountId()).orElse(null);
            return new T24HoldResponse(
                    hold.getHoldId(),
                    hold.getAccountId(),
                    acc != null ? acc.getAccountNumber() : null,
                    hold.getAmount(),
                    hold.getCurrency(),
                    hold.getReferenceNo(),
                    hold.getStatus(),
                    acc != null ? acc.getAvailableBalance() : BigDecimal.ZERO,
                    hold.getCreatedAt(),
                    "Hold status retrieved"
            );
        });
    }
}
