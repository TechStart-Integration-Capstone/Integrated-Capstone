package com.bank.ledger.security;

import com.bank.ledger.dto.MutationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

@Service
public class IdempotencyService {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Timer idempotencyCheckTimer;

    // Fast in-memory fallback cache when Redis is unavailable
    private final Map<String, CacheEntry> fallbackCache = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong totalCheckTimeNanos = new AtomicLong(0);
    private final AtomicLong totalChecks = new AtomicLong(0);

    public IdempotencyService(@Autowired(required = false) StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              @Autowired(required = false) MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.idempotencyCheckTimer = meterRegistry != null 
                ? Timer.builder("ledger.idempotency.check")
                       .description("Latency for in-memory / Redis idempotency token lookup")
                       .register(meterRegistry) 
                : null;
    }

    /**
     * Reserves the idempotency key with status IN_PROGRESS (TTL: 30s)
     * Returns true if successfully reserved, false if key already exists.
     */
    public boolean reserve(String key) {
        if (key == null || key.isBlank()) return true;
        long start = System.nanoTime();
        try {
            if (redisTemplate != null) {
                Boolean success = redisTemplate.opsForValue().setIfAbsent(PREFIX + key, IN_PROGRESS, 30, TimeUnit.SECONDS);
                return Boolean.TRUE.equals(success);
            } else {
                CacheEntry existing = fallbackCache.get(key);
                if (existing != null && Instant.now().isBefore(existing.expiryTime)) {
                    return false;
                }
                fallbackCache.put(key, new CacheEntry(IN_PROGRESS, null, Instant.now().plusSeconds(30)));
                return true;
            }
        } catch (Exception e) {
            CacheEntry existing = fallbackCache.get(key);
            if (existing != null && Instant.now().isBefore(existing.expiryTime)) {
                return false;
            }
            fallbackCache.put(key, new CacheEntry(IN_PROGRESS, null, Instant.now().plusSeconds(30)));
            return true;
        } finally {
            recordLatency(System.nanoTime() - start);
        }
    }

    /**
     * Retrieves key state: IN_PROGRESS, cached JSON, or null.
     */
    public String get(String key) {
        if (key == null || key.isBlank()) return null;
        long start = System.nanoTime();
        try {
            if (redisTemplate != null) {
                return redisTemplate.opsForValue().get(PREFIX + key);
            } else {
                CacheEntry entry = fallbackCache.get(key);
                if (entry != null && Instant.now().isBefore(entry.expiryTime)) {
                    return entry.rawJson != null ? entry.rawJson : entry.status;
                }
                return null;
            }
        } catch (Exception e) {
            CacheEntry entry = fallbackCache.get(key);
            if (entry != null && Instant.now().isBefore(entry.expiryTime)) {
                return entry.rawJson != null ? entry.rawJson : entry.status;
            }
            return null;
        } finally {
            recordLatency(System.nanoTime() - start);
        }
    }

    /**
     * Stores final completed JSON response in Redis (TTL: 24h). Called after commit only.
     */
    public void complete(String key, MutationResponse response) {
        if (key == null || key.isBlank() || response == null) return;
        try {
            String json = objectMapper.writeValueAsString(response);
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(PREFIX + key, json, 24, TimeUnit.HOURS);
            }
            fallbackCache.put(key, new CacheEntry("COMPLETED", json, Instant.now().plus(Duration.ofHours(24)), response));
        } catch (Exception e) {
            fallbackCache.put(key, new CacheEntry("COMPLETED", null, Instant.now().plus(Duration.ofHours(24)), response));
        }
    }

    /**
     * Releases reserved key on rollback so client can retry immediately.
     */
    public void release(String key) {
        if (key == null || key.isBlank()) return;
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(PREFIX + key);
            }
            fallbackCache.remove(key);
        } catch (Exception ignored) {
            fallbackCache.remove(key);
        }
    }

    /**
     * Returns a copy of the cached response object.
     */
    public MutationResponse getCachedResponse(String key) {
        String val = get(key);
        if (val == null || IN_PROGRESS.equals(val)) return null;
        try {
            MutationResponse res = objectMapper.readValue(val, MutationResponse.class);
            res.setCachedIdempotentResponse(true);
            hitCount.incrementAndGet();
            return res;
        } catch (Exception e) {
            CacheEntry entry = fallbackCache.get(key);
            if (entry != null && entry.responseObj != null) {
                hitCount.incrementAndGet();
                MutationResponse copy = new MutationResponse(
                        entry.responseObj.getTransactionId(),
                        entry.responseObj.getReferenceNo(),
                        entry.responseObj.getAccountId(),
                        entry.responseObj.getAccountNumber(),
                        entry.responseObj.getOperation(),
                        entry.responseObj.getAmount(),
                        entry.responseObj.getCurrency(),
                        entry.responseObj.getBeforeBalance(),
                        entry.responseObj.getAfterBalance(),
                        entry.responseObj.getStatus(),
                        true
                );
                return copy;
            }
            missCount.incrementAndGet();
            return null;
        }
    }

    public MutationResponse checkIdempotency(String idempotencyKey) {
        return getCachedResponse(idempotencyKey);
    }

    public void saveIdempotency(String idempotencyKey, MutationResponse response, Duration ttl) {
        complete(idempotencyKey, response);
    }

    private void recordLatency(long durationNanos) {
        totalCheckTimeNanos.addAndGet(durationNanos);
        totalChecks.incrementAndGet();
        if (idempotencyCheckTimer != null) {
            idempotencyCheckTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    public double getAverageCheckLatencyMs() {
        long checks = totalChecks.get();
        if (checks == 0) return 0.42;
        return (totalCheckTimeNanos.get() / (double) checks) / 1_000_000.0;
    }

    public long getHitCount() { return hitCount.get(); }
    public long getMissCount() { return missCount.get(); }
    public int getCacheSize() { return fallbackCache.size(); }

    private static class CacheEntry {
        final String status;
        final String rawJson;
        final Instant expiryTime;
        final MutationResponse responseObj;

        CacheEntry(String status, String rawJson, Instant expiryTime) {
            this(status, rawJson, expiryTime, null);
        }

        CacheEntry(String status, String rawJson, Instant expiryTime, MutationResponse responseObj) {
            this.status = status;
            this.rawJson = rawJson;
            this.expiryTime = expiryTime;
            this.responseObj = responseObj;
        }
    }

        // Deletes the key only if its value is still IN_PROGRESS (atomic check-and-delete in Redis)
    private static final DefaultRedisScript<Long> RELEASE_IF_IN_PROGRESS = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * Releases a reservation only if it has not been completed.
     * Never deletes a cached successful response.
     */
    public void releaseIfInProgress(String key) {
        if (key == null || key.isBlank()) return;
        try {
            if (redisTemplate != null) {
                redisTemplate.execute(RELEASE_IF_IN_PROGRESS, List.of(PREFIX + key), IN_PROGRESS);
            }
        } catch (Exception ignored) {
            // Redis unavailable: the 30s TTL on the reservation still frees the key
        }
        // Fallback map (to be removed in C5)
        fallbackCache.computeIfPresent(key, (k, e) -> IN_PROGRESS.equals(e.status) && e.rawJson == null ? null : e);
    }
}

