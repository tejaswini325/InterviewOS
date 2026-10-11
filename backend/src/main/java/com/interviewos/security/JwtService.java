package com.interviewos.security;

import com.interviewos.config.AppProperties;
import com.interviewos.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final AppProperties props;
    private final Clock clock;
    private SecretKey key;

    public JwtService(AppProperties props, Clock clock) { this.props = props; this.clock = clock; }

    @PostConstruct
    void init() {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record Issued(String token, Instant expiresAt) {}

    public Issued issue(Long userId, String email, Role role) {
        Instant now = clock.instant();
        Instant exp = now.plus(Duration.ofMinutes(props.jwt().expiryMinutes()));
        String token = Jwts.builder().subject(String.valueOf(userId)).claim("email", email).claim("role", role.name())
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(now)).expiration(Date.from(exp)).signWith(key).compact();
        return new Issued(token, exp);
    }

    /** Throws io.jsonwebtoken.JwtException when the token is invalid or expired. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build().parseSignedClaims(token).getPayload();
    }
}
