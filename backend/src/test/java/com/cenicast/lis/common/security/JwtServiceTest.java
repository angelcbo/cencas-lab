package com.cenicast.lis.common.security;

import com.cenicast.lis.common.exception.ApiException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test — no Spring context loaded.
 * Constructs JwtService directly with a fixed test secret.
 */
class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-at-least-32-chars-long!!";
    private static final int EXPIRY_MINUTES = 15;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRY_MINUTES);
    }

    @Test
    void issueAndValidateRoundTrip() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, tenantId, "user@test.com", "", "LAB_ADMIN", true);

        String token = jwtService.generateAccessToken(principal);
        Claims claims = jwtService.validateAndExtract(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwtService.extractTenantId(claims)).isEqualTo(tenantId);
        assertThat(jwtService.extractRole(claims)).isEqualTo("LAB_ADMIN");
        assertThat(jwtService.extractEmail(claims)).isEqualTo("user@test.com");
    }

    @Test
    void issueAndValidateRoundTrip_superAdmin_noTenantId() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, null, "admin@test.com", "", "SUPER_ADMIN", true);

        String token = jwtService.generateAccessToken(principal);
        Claims claims = jwtService.validateAndExtract(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwtService.extractTenantId(claims)).isNull();
        assertThat(jwtService.extractRole(claims)).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void expiredToken_throws401() {
        // JwtService with -1 minute expiry → token is already expired when issued
        JwtService expiredService = new JwtService(TEST_SECRET, -1);
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "x@test.com", "", "LAB_ADMIN", true);
        String token = expiredService.generateAccessToken(principal);

        assertThatThrownBy(() -> jwtService.validateAndExtract(token))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void tamperedSignature_throws401() {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), null, "x@test.com", "", "SUPER_ADMIN", true);
        String token = jwtService.generateAccessToken(principal);

        // Corrupt the last 4 characters of the signature (final JWT segment)
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertThatThrownBy(() -> jwtService.validateAndExtract(tampered))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
