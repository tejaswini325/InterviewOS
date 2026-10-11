package com.interviewos.config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used when Redis is disabled (tests, single-node demos). Not shared between instances. */
@Component
@ConditionalOnProperty(name = "interviewos.redis.enabled", havingValue = "false")
public class InMemoryKeyValueStore implements KeyValueStore {
    private record Entry(long value, Instant expires) {}
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    @Override public long increment(String key, Duration ttlIfNew) {
        Instant now = Instant.now();
        return map.compute(key, (k, e) -> e == null || e.expires().isBefore(now)
                ? new Entry(1, now.plus(ttlIfNew)) : new Entry(e.value() + 1, e.expires())).value();
    }
    @Override public void put(String key, Duration ttl) { map.put(key, new Entry(1, Instant.now().plus(ttl))); }
    @Override public boolean exists(String key) {
        Entry e = map.get(key);
        return e != null && e.expires().isAfter(Instant.now());
    }
}
