package com.interviewos.security;

import com.interviewos.config.KeyValueStore;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Makes logout real: the token id is kept in Redis until the token would have expired anyway. */
@Component
public class TokenDenylist {
    private static final Logger log = LoggerFactory.getLogger(TokenDenylist.class);
    private final KeyValueStore store;

    public TokenDenylist(KeyValueStore store) { this.store = store; }

    public void revoke(Claims claims) {
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!ttl.isNegative() && !ttl.isZero()) store.put("jwt:denied:" + claims.getId(), ttl);
    }

    public boolean isRevoked(Claims claims) {
        try {
            return store.exists("jwt:denied:" + claims.getId());
        } catch (RuntimeException e) {   // fail open: availability over strictness; the token is still signed and short-lived
            log.warn("Token denylist unavailable: {}", e.getMessage());
            return false;
        }
    }
}
