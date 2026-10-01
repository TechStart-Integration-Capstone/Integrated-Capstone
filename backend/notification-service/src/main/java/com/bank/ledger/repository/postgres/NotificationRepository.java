package com.bank.ledger.repository.postgres;

import com.bank.ledger.model.postgres.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByReferenceNoAndAccountId(String referenceNo, Long accountId);
    List<Notification> findByCustomerIdOrderByCreatedDateDesc(Long customerId);
    List<Notification> findTop50ByOrderByCreatedDateDesc();
}
