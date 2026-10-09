package com.bank.t24.repository;

import com.bank.t24.model.LockedAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LockedAmountRepository extends JpaRepository<LockedAmount, Long> {

    Optional<LockedAmount> findByReferenceNo(String referenceNo);

    List<LockedAmount> findByAccountIdAndStatus(Long accountId, String status);
}
