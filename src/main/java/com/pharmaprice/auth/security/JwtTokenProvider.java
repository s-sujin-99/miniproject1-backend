package com.pharmaprice.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pharmaprice.auth.domain.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 발급/검증(ROADMAP T-23, shrimp-rules.md §5.2). Access Token 단일(24시간) —
 * Refresh Token 발급·rotation·저장은 이 프로젝트에서 구현하지 않는다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** 클레임: sub=userId, email, role. 비밀번호·개인정보는 넣지 않는다(shrimp-rules.md §5.2). */
    public String generateToken(Long userId, String email, UserRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 서명 불일치·만료·형식 오류 시 io.jsonwebtoken.JwtException을 던진다 — 호출자(필터)가 잡아 미인증으로 통과시킨다. */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        UserRole role = UserRole.valueOf(claims.get("role", String.class));
        return new AuthenticatedUser(userId, email, role);
    }
}
