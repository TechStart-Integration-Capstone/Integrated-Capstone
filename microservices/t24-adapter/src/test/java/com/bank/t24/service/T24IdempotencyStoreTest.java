package com.bank.t24.service;

import com.bank.t24.dto.T24TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class T24IdempotencyStoreTest {

    private T24IdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new T24IdempotencyStore();
    }

    @Test
    @DisplayName("Should return empty optional when key does not exist")
    void testGetEmpty() {
        Optional<T24TransferResponse> res = store.get("NON-EXISTENT");
        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("Should store and retrieve idempotent response")
    void testPutAndGet() {
        T24TransferResponse response = T24TransferResponse.success("FT12345", "FT12345/1", false);
        store.put("TX-001", response);

        Optional<T24TransferResponse> retrieved = store.get("TX-001");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getFtReference()).isEqualTo("FT12345");
    }
}
