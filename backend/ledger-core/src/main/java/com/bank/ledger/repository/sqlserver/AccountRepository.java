package com.bank.ledger.repository.sqlserver;

import com.bank.ledger.model.sqlserver.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Critical Concurrency Isolation Method (Day 33 Requirement)
     * Acquires a pessimistic write lock for this account row.
     * Forces deterministic thread queueing at database row level.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerId(Long customerId);
}
