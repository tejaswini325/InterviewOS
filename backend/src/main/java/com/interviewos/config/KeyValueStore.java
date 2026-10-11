package com.interviewos.config;

import java.time.Duration;

/** Small abstraction over Redis for temporary state (rate-limit counters, revoked token ids). */
public interface KeyValueStore {
    /** Atomically increments a counter; the TTL starts when the counter is created. */
    long increment(String key, Duration ttlIfNew);
    void put(String key, Duration ttl);
    boolean exists(String key);
}
