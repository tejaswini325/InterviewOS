package com.interviewos.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "interviewos.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisKeyValueStore implements KeyValueStore {
    private final StringRedisTemplate redis;

    public RedisKeyValueStore(StringRedisTemplate redis) { this.redis = redis; }

    @Override public long increment(String key, Duration ttlIfNew) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) redis.expire(key, ttlIfNew);
        return v == null ? 0 : v;
    }
    @Override public void put(String key, Duration ttl) { redis.opsForValue().set(key, "1", ttl); }
    @Override public boolean exists(String key) { return Boolean.TRUE.equals(redis.hasKey(key)); }
}
