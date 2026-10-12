package com.pos.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pos.auth.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    private static final String DEV_SECRET_PREFIX = "change-me";
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public JwtService(
            @Value("${pos.jwt.secret}") String secret,
            @Value("${pos.jwt.expiration-ms}") long expirationMs) {
        if (secret.startsWith(DEV_SECRET_PREFIX)) {
            log.warn("pos.jwt.secret is the built-in development default; set JWT_SECRET before deploying");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user, UUID registerId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)));
        if (registerId != null) {
            builder.claim("registerId", registerId.toString());
        }
        return builder.signWith(key).compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Optional<UUID> registerIdFrom(Claims claims) {
        return Optional.ofNullable(claims.get("registerId", String.class)).map(UUID::fromString);
    }
}
