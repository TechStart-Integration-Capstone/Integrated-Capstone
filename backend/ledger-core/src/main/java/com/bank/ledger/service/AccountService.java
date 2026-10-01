package com.bank.ledger.service;

import com.bank.ledger.dto.AccountDto;
import com.bank.ledger.dto.CustomerDto;
import com.bank.ledger.exception.AccountNotFoundException;
import com.bank.ledger.model.oracle.Account;
import com.bank.ledger.model.oracle.Customer;
import com.bank.ledger.repository.oracle.AccountRepository;
import com.bank.ledger.repository.oracle.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountById(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account ID " + accountId + " not found."));
        return mapToDto(account);
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAccountsByCustomerId(Long customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CustomerDto getCustomerProfile(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        CustomerDto dto = new CustomerDto(
                customer.getCustomerId(),
                customer.getUsername(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getContactNo(),
                customer.getStatus(),
                customer.getCreatedDate()
        );
        dto.setAccounts(getAccountsByCustomerId(customerId));
        return dto;
    }

    @Transactional
    public AccountDto resetAccountBalance(Long accountId, BigDecimal targetBalance) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found."));
        account.setCurrentBalance(targetBalance);
        return mapToDto(accountRepository.save(account));
    }

    private AccountDto mapToDto(Account a) {
        return new AccountDto(
                a.getAccountId(),
                a.getCustomerId(),
                a.getAccountNumber(),
                a.getAccountType(),
                a.getCurrency(),
                a.getCurrentBalance(),
                a.getStatus(),
                a.getCreatedDate()
        );
    }
}
