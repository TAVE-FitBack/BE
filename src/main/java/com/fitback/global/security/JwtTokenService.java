package com.fitback.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenService {
    private final SecretKey key;
    private final long expirationMillis;

    public JwtTokenService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String issue(String subject) {
        Instant now = Instant.now();
        return Jwts.builder().subject(subject).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis))).signWith(key).compact();
    }

    public String subject(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }
}
