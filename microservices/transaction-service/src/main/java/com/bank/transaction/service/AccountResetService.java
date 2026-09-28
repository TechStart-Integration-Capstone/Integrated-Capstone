package com.bank.transaction.service;

import com.bank.transaction.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Thin service used exclusively by StressTestController to reset an account's
 * balance before each stress run. Kept separate so the @Transactional proxy
 * is honoured by Spring (self-invocation from a controller doesn't go through
 * the proxy, but a call to an injected @Service bean does).
 */
@Service
public class AccountResetService {

    private final AccountRepository accountRepository;

    public AccountResetService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void resetBalance(Long accountId, BigDecimal targetBalance) {
        accountRepository.findById(accountId).ifPresent(acc -> {
            acc.setCurrentBalance(targetBalance);
            accountRepository.save(acc);
        });
    }
}
