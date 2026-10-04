package com.bank.t24.service;

import com.bank.t24.dto.T24TransferResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency store for T24 Core Adapter.
 * Guarantees that sending the same reference number multiple times returns the same FT reference.
 * Prevents double-posting in Core Banking.
 */
@Component
public class T24IdempotencyStore {

    private final Map<String, T24TransferResponse> cache = new ConcurrentHashMap<>();

    public Optional<T24TransferResponse> get(String referenceNo) {
        return Optional.ofNullable(cache.get(referenceNo));
    }

    public void put(String referenceNo, T24TransferResponse response) {
        cache.put(referenceNo, response);
    }
}
