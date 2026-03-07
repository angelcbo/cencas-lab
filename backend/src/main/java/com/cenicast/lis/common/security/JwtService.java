package com.cenicast.lis.common.security;

import com.cenicast.lis.common.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.MINUTES;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final int expiryMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-minutes}") int expiryMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    public String generateAccessToken(UserPrincipal principal) {
        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("tenantId", principal.getTenantId() != null ? principal.getTenantId().toString() : null)
                .claim("role", principal.getRole())
                .claim("email", principal.getEmail())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expiryMinutes, MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token and returns its claims.
     * Throws ApiException(401) on any invalid/expired token — never returns null.
     */
    public Claims validateAndExtract(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /** Returns null for SUPER_ADMIN tokens (no tenantId claim). */
    public UUID extractTenantId(Claims claims) {
        String tenantId = claims.get("tenantId", String.class);
        return tenantId != null ? UUID.fromString(tenantId) : null;
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public String extractEmail(Claims claims) {
        return claims.get("email", String.class);
    }
}
