package com.prathamesh.jbsolar.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.prathamesh.jbsolar.domain.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-seconds:3600}") long expirationSeconds) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("app.security.jwt-secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject());
    }

    public long getExpirationSeconds() { return expirationSeconds; }
}
