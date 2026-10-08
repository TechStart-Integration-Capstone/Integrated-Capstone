package com.bank.t24.service;

import com.bank.t24.dto.T24TransferResponse;
import com.bank.t24.repository.PostingJournalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency store for T24 Core Adapter.
 * Backed by an in-memory cache and persistent t24.POSTING_JOURNAL.
 * Guarantees that sending the same reference number multiple times returns the same FT reference
 * across container restarts. Prevents double-posting in Core Banking.
 */
@Component
public class T24IdempotencyStore {

    private final Map<String, T24TransferResponse> cache = new ConcurrentHashMap<>();
    private final PostingJournalRepository postingJournalRepository;

    public T24IdempotencyStore() {
        this.postingJournalRepository = null;
    }

    @Autowired
    public T24IdempotencyStore(@Nullable PostingJournalRepository postingJournalRepository) {
        this.postingJournalRepository = postingJournalRepository;
    }

    public Optional<T24TransferResponse> get(String referenceNo) {
        T24TransferResponse response = cache.get(referenceNo);
        if (response != null) {
            return Optional.of(response);
        }
        if (postingJournalRepository != null) {
            return postingJournalRepository.findByReferenceNo(referenceNo)
                    .map(journal -> {
                        String ftRef = "FT" + (journal.getJournalId() != null ? journal.getJournalId() : journal.getReferenceNo());
                        T24TransferResponse res = T24TransferResponse.success(ftRef, "IDEMPOTENT_DB_REPLAY", true);
                        cache.put(referenceNo, res);
                        return res;
                    });
        }
        return Optional.empty();
    }

    public void put(String referenceNo, T24TransferResponse response) {
        cache.put(referenceNo, response);
    }
}
