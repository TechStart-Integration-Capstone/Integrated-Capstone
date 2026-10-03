package com.bank.account;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.CustomerDto;
import com.bank.account.model.Account;
import com.bank.account.model.Customer;
import com.bank.account.repository.AccountRepository;
import com.bank.account.repository.CustomerRepository;
import com.bank.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Unit tests for AccountService — no database connection needed. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CustomerRepository customerRepository;
    @InjectMocks private AccountService accountService;

    private Account acc1, acc2;
    private Customer customer1;

    private void setField(Object obj, String name, Object val) {
        try { var fld = obj.getClass().getDeclaredField(name); fld.setAccessible(true); fld.set(obj, val); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @BeforeEach void setUp() {
        acc1 = new Account(); setField(acc1, "accountId", 1L); setField(acc1, "customerId", 10L);
        setField(acc1, "accountNumber", "ACC-001"); setField(acc1, "accountType", "SAVINGS");
        setField(acc1, "currency", "PHP"); setField(acc1, "currentBalance", new BigDecimal("1000.0000"));
        setField(acc1, "status", "ACTIVE");

        acc2 = new Account(); setField(acc2, "accountId", 2L); setField(acc2, "customerId", 10L);
        setField(acc2, "accountNumber", "ACC-002"); setField(acc2, "accountType", "CHECKING");
        setField(acc2, "currency", "PHP"); setField(acc2, "currentBalance", new BigDecimal("500.0000"));
        setField(acc2, "status", "ACTIVE");

        customer1 = new Customer(); setField(customer1, "customerId", 10L);
        setField(customer1, "username", "jdelacruz"); setField(customer1, "firstName", "Juan");
        setField(customer1, "lastName", "Dela Cruz"); setField(customer1, "email", "juan@paypink.ph");
        setField(customer1, "status", "ACTIVE");
    }


    @Test @DisplayName("getAllAccounts: returns mapped DTOs for all accounts")
    void getAllAccounts_returnsDtos(){
        when(accountRepository.findAll()).thenReturn(List.of(acc1,acc2));
        List<AccountDto> result=accountService.getAllAccounts();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC-001");
        assertThat(result.get(1).getAccountNumber()).isEqualTo("ACC-002");
    }
    @Test @DisplayName("getAllAccounts: empty table returns empty list")
    void getAllAccounts_emptyTable_returnsEmptyList(){
        when(accountRepository.findAll()).thenReturn(List.of());
        assertThat(accountService.getAllAccounts()).isEmpty();
    }
    @Test @DisplayName("getAccountById: existing id returns correct DTO")
    void getAccountById_existingId_returnsDto(){
        when(accountRepository.findById(1L)).thenReturn(Optional.of(acc1));
        AccountDto dto=accountService.getAccountById(1L);
        assertThat(dto.getAccountId()).isEqualTo(1L);
        assertThat(dto.getAccountNumber()).isEqualTo("ACC-001");
        assertThat(dto.getCurrentBalance()).isEqualByComparingTo("1000.0000");
    }
    @Test @DisplayName("getAccountById: unknown id throws RuntimeException")
    void getAccountById_unknownId_throws(){
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->accountService.getAccountById(99L)).isInstanceOf(RuntimeException.class).hasMessageContaining("99");
    }
    @Test @DisplayName("getCustomerProfile: returns customer with linked accounts")
    void getCustomerProfile_returnsCustomerWithAccounts(){
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer1));
        when(accountRepository.findByCustomerId(10L)).thenReturn(List.of(acc1,acc2));
        CustomerDto dto=accountService.getCustomerProfile(10L);
        assertThat(dto.getCustomerId()).isEqualTo(10L);
        assertThat(dto.getUsername()).isEqualTo("jdelacruz");
        assertThat(dto.getAccounts()).hasSize(2);
    }
    @Test @DisplayName("getCustomerProfile: unknown customerId throws RuntimeException")
    void getCustomerProfile_unknownId_throws(){
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->accountService.getCustomerProfile(99L)).isInstanceOf(RuntimeException.class);
    }
    @Test @DisplayName("getCustomerProfile: customer with no accounts returns empty account list")
    void getCustomerProfile_noAccounts_returnsEmptyList(){
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer1));
        when(accountRepository.findByCustomerId(10L)).thenReturn(List.of());
        assertThat(accountService.getCustomerProfile(10L).getAccounts()).isEmpty();
    }
    @Test @DisplayName("resetAccountBalance: updates balance and returns updated DTO")
    void resetAccountBalance_updatesBalance(){
        when(accountRepository.findById(1L)).thenReturn(Optional.of(acc1));
        when(accountRepository.save(acc1)).thenReturn(acc1);
        AccountDto dto=accountService.resetAccountBalance(1L,new BigDecimal("2500.0000"));
        assertThat(dto.getCurrentBalance()).isEqualByComparingTo("2500.0000");
        verify(accountRepository).save(acc1);
    }
    @Test @DisplayName("getAccountById: DTO currency matches account currency")
    void getAccountById_dtoCurrencyMatchesAccount(){
        when(accountRepository.findById(1L)).thenReturn(Optional.of(acc1));
        assertThat(accountService.getAccountById(1L).getCurrency()).isEqualTo("PHP");
    }
}
