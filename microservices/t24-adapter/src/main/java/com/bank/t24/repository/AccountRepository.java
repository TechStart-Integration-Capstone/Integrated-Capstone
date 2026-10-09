package com.bank.t24.repository;

import com.bank.t24.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query(value = "SELECT COALESCE((SELECT SUM(reserved_amount) FROM t24.SAVINGS_RESERVATION WHERE account_id=:accountId),0) + COALESCE((SELECT SUM(amount) FROM t24.LOCKED_AMOUNT WHERE account_id=:accountId AND status='ACTIVE'),0)", nativeQuery = true)
    java.math.BigDecimal recordedHolds(@Param("accountId") Long accountId);

    // Caller must hold the account write lock. Never clear an unclassified hold.
    default void protectReservations(Account account) {
        account.setHeldBalance(account.getHeldBalance().max(recordedHolds(account.getAccountId())));
    }

    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
