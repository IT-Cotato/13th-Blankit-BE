package com.cotato.blankit.global.security;

import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String USER_ID_CLAIM = "userId";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMillis,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMillis
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, ACCESS_TOKEN_TYPE, accessTokenExpirationMillis);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, REFRESH_TOKEN_TYPE, refreshTokenExpirationMillis);
    }

    public Long getUserIdFromAccessToken(String token) {
        return getUserId(token, ACCESS_TOKEN_TYPE);
    }

    public Long getUserIdFromRefreshToken(String token) {
        return getUserId(token, REFRESH_TOKEN_TYPE);
    }

    public LocalDateTime getRefreshTokenExpiresAt() {
        return LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpirationMillis));
    }

    private String createToken(Long userId, String tokenType, long expirationMillis) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim(USER_ID_CLAIM, userId)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(secretKey)
                .compact();
    }

    private Long getUserId(String token, String expectedTokenType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedTokenType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                throw new CustomException(ErrorCode.INVALID_TOKEN);
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, e);
        }
    }
}
