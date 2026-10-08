package com.bank.account.service;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.CustomerDto;
import com.bank.account.model.Account;
import com.bank.account.model.Customer;
import com.bank.account.repository.AccountRepository;
import com.bank.account.repository.CustomerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String CACHE_PREFIX = "account:balance:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.bank.account.client.T24AccountClient t24AccountClient;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper) {
        this(accountRepository, customerRepository, redisTemplate, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          com.bank.account.client.T24AccountClient t24AccountClient) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.t24AccountClient = t24AccountClient;
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAccountsByCustomerId(Long customerId) {
        return accountRepository.findByCustomerId(customerId).stream().map(this::mapToDto).collect(Collectors.toList());
    }

    /**
     * Gets a single account by ID.
     * On success: writes the result to Redis (30s TTL) for circuit breaker fallback.
     * On DB failure: circuit opens and falls back to Redis cached value (display-only).
     *
     * AGENTS.md rule: cached balances are DISPLAY-ONLY — never used to approve a transfer.
     */
    @CircuitBreaker(name = "accountDb", fallbackMethod = "getAccountByIdFallback")
    @Transactional(readOnly = true)
    public AccountDto getAccountById(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Account ID " + accountId + " not found."));
        AccountDto dto = mapToDto(account);
        cacheAccount(dto);
        return dto;
    }

    /**
     * Fallback for getAccountById — returns Redis cached balance when DB is unavailable.
     * Response includes mayBeStale=true so the caller knows this is not a live value.
     */
    public AccountDto getAccountByIdFallback(Long accountId, Throwable ex) {
        if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            throw rse;
        }
        log.warn("[account-service] Circuit breaker fallback for accountId={} reason={}",
                accountId, ex.getMessage());
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + accountId);
        if (cached != null) {
            try {
                AccountDto dto = objectMapper.readValue(cached, AccountDto.class);
                dto.setMayBeStale(true);
                return dto;
            } catch (Exception e) {
                log.error("[account-service] Failed to deserialize cached account {}: {}", accountId, e.getMessage());
            }
        }
        throw new RuntimeException("Account ID " + accountId + " is currently unavailable. Please try again shortly.");
    }

    /**
     * Gets customer profile with all accounts.
     * Circuit breaker wraps the whole method — falls back to cached accounts list.
     */
    @CircuitBreaker(name = "accountDb", fallbackMethod = "getCustomerProfileFallback")
    @Transactional(readOnly = true)
    public CustomerDto getCustomerProfile(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Customer not found"));
        CustomerDto dto = new CustomerDto(customer.getCustomerId(), customer.getUsername(),
                customer.getFirstName(), customer.getLastName(), customer.getEmail(),
                customer.getContactNo(), customer.getStatus(), customer.getCreatedDate());
        List<AccountDto> accounts = accountRepository.findByCustomerId(customerId)
                .stream().map(this::mapToDto).collect(Collectors.toList());
        // Cache each account individually for fallback
        accounts.forEach(this::cacheAccount);
        dto.setAccounts(accounts);
        return dto;
    }

    public CustomerDto getCustomerProfileFallback(Long customerId, Throwable ex) {
        if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            throw rse;
        }
        log.warn("[account-service] Circuit breaker fallback for customerId={} reason={}",
                customerId, ex.getMessage());
        throw new RuntimeException("Customer profile is currently unavailable. Please try again shortly.");
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> getAllCustomers() {
        return customerRepository.findAll().stream().map(c -> {
            CustomerDto dto = new CustomerDto(c.getCustomerId(), c.getUsername(),
                    c.getFirstName(), c.getLastName(), c.getEmail(),
                    c.getContactNo(), c.getStatus(), c.getCreatedDate());
            List<AccountDto> accounts = accountRepository.findByCustomerId(c.getCustomerId())
                    .stream().map(this::mapToDto).collect(Collectors.toList());
            dto.setAccounts(accounts);
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public AccountDto updateAccountStatus(Long accountId, String status) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Account ID " + accountId + " not found."));
        account.setStatus(status.toUpperCase());
        return mapToDto(accountRepository.save(account));
    }

    @Transactional
    public CustomerDto updateCustomerStatus(Long customerId, String status) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Customer ID " + customerId + " not found."));
        customer.setStatus(status.toUpperCase());
        customerRepository.save(customer);
        return getCustomerProfile(customerId);
    }

    @Transactional
    public AccountDto resetAccountBalance(Long accountId, BigDecimal targetBalance) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Account not found."));
        account.setCurrentBalance(targetBalance);
        AccountDto dto = mapToDto(accountRepository.save(account));
        cacheAccount(dto);
        return dto;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void cacheAccount(AccountDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(CACHE_PREFIX + dto.getAccountId(), json, CACHE_TTL);
        } catch (Exception e) {
            // Non-fatal — cache miss just means fallback won't have data
            log.warn("[account-service] Failed to cache account {}: {}", dto.getAccountId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public com.bank.account.dto.UserProfileDto getUserProfile(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Please log in again."));

        List<com.bank.account.dto.UserProfileDto.AccountSummaryDto> summaries = accountRepository.findByCustomerId(customerId)
                .stream().map(a -> {
                    BigDecimal balance = a.getCurrentBalance();
                    if (t24AccountClient != null) {
                        var live = t24AccountClient.getLiveBalance(a.getAccountNumber());
                        if (live.isPresent()) {
                            balance = live.get().currentBalance();
                        }
                    }
                    return new com.bank.account.dto.UserProfileDto.AccountSummaryDto(
                            a.getAccountId(),
                            a.getAccountNumber(),
                            a.getAccountType(),
                            a.getCurrency(),
                            balance,
                            a.getStatus()
                    );
                }).collect(Collectors.toList());

        return new com.bank.account.dto.UserProfileDto(
                customer.getFirstName(),
                customer.getFirstName() + " " + customer.getLastName(),
                customer.getUsername(),
                customer.getEmail(),
                summaries
        );
    }

    private AccountDto mapToDto(Account a) {
        BigDecimal balance = a.getCurrentBalance();
        if (t24AccountClient != null) {
            var live = t24AccountClient.getLiveBalance(a.getAccountNumber());
            if (live.isPresent()) {
                balance = live.get().currentBalance();
            }
        }
        return new AccountDto(a.getAccountId(), a.getCustomerId(), a.getAccountNumber(),
                a.getAccountType(), a.getCurrency(), balance, a.getStatus(), a.getCreatedDate());
    }
}
