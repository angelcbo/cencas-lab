package com.cenicast.lis.auth;

import com.cenicast.lis.auth.repository.RefreshTokenRepository;
import com.cenicast.lis.common.audit.AuditEventRepository;
import com.cenicast.lis.common.security.JwtService;
import com.cenicast.lis.tenant.model.Tenant;
import com.cenicast.lis.tenant.repository.TenantRepository;
import com.cenicast.lis.users.model.Role;
import com.cenicast.lis.users.model.User;
import com.cenicast.lis.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AuditEventRepository auditEventRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    // Known credentials from DataInitializer bootstrap (SUPER_ADMIN)
    private static final String SUPER_ADMIN_EMAIL = "admin@cenicast.com";
    private static final String SUPER_ADMIN_PASSWORD = "ChangeMe123!";

    // Test-specific isolated tenants (created once per test run via flag)
    private static final String LAB_A_SLUG = "integration-test-lab-a";
    private static final String LAB_A_EMAIL = "admin-a@integration-test.com";
    private static final String LAB_A_PASSWORD = "TestPass123!";
    private static final String LAB_B_SLUG = "integration-test-lab-b";
    private static final String LAB_B_EMAIL = "admin-b@integration-test.com";
    private static final String LAB_B_PASSWORD = "TestPass456!";

    // Static state — set up once across all tests in this context
    private static UUID tenantAId;
    private static UUID tenantBId;
    private static UUID userBId;
    private static volatile boolean setupComplete = false;

    @BeforeEach
    void ensureTestDataExists() {
        if (setupComplete) return;
        synchronized (AuthIntegrationTest.class) {
            if (setupComplete) return;

            // Tenant A
            Tenant tenantA = tenantRepository.findBySlug(LAB_A_SLUG).orElseGet(() -> {
                Tenant t = new Tenant();
                t.setSlug(LAB_A_SLUG);
                t.setName("Integration Test Lab A");
                t.setTimezone("America/Mexico_City");
                t.setTaxRate(new BigDecimal("0.1600"));
                t.setActive(true);
                return tenantRepository.save(t);
            });
            tenantAId = tenantA.getId();

            userRepository.findByEmailAndTenantId(LAB_A_EMAIL, tenantAId).orElseGet(() -> {
                User u = new User();
                u.setTenantId(tenantAId);
                u.setEmail(LAB_A_EMAIL);
                u.setPasswordHash(passwordEncoder.encode(LAB_A_PASSWORD));
                u.setFirstName("Admin");
                u.setLastName("A");
                u.setRole(Role.LAB_ADMIN);
                u.setActive(true);
                return userRepository.save(u);
            });

            // Tenant B
            Tenant tenantB = tenantRepository.findBySlug(LAB_B_SLUG).orElseGet(() -> {
                Tenant t = new Tenant();
                t.setSlug(LAB_B_SLUG);
                t.setName("Integration Test Lab B");
                t.setTimezone("America/Mexico_City");
                t.setTaxRate(new BigDecimal("0.1600"));
                t.setActive(true);
                return tenantRepository.save(t);
            });
            tenantBId = tenantB.getId();

            User userB = userRepository.findByEmailAndTenantId(LAB_B_EMAIL, tenantBId).orElseGet(() -> {
                User u = new User();
                u.setTenantId(tenantBId);
                u.setEmail(LAB_B_EMAIL);
                u.setPasswordHash(passwordEncoder.encode(LAB_B_PASSWORD));
                u.setFirstName("Admin");
                u.setLastName("B");
                u.setRole(Role.LAB_ADMIN);
                u.setActive(true);
                return userRepository.save(u);
            });
            userBId = userB.getId();

            setupComplete = true;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Login tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void login_badPassword_returns401() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/auth/login", loginBody(LAB_A_EMAIL, "WrongPassword!", LAB_A_SLUG), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void login_inactiveUser_returns401() {
        String inactiveEmail = "inactive-" + UUID.randomUUID() + "@integration-test.com";
        User inactive = new User();
        inactive.setTenantId(tenantAId);
        inactive.setEmail(inactiveEmail);
        inactive.setPasswordHash(passwordEncoder.encode("InactivePass123!"));
        inactive.setFirstName("In");
        inactive.setLastName("Active");
        inactive.setRole(Role.LAB_ANALYST);
        inactive.setActive(false);
        userRepository.save(inactive);

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/auth/login", loginBody(inactiveEmail, "InactivePass123!", LAB_A_SLUG), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void login_validLabAdmin_returns200AndSetsCookie() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/auth/login", loginBody(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
        assertThat(response.getBody().get("tokenType")).isEqualTo("Bearer");

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull()
                .contains("refresh_token=")
                .contains("HttpOnly")
                .contains("Path=/api/v1/auth");
    }

    @Test
    @SuppressWarnings("unchecked")
    void login_superAdmin_returns200_noTenantId() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/auth/login", loginBody(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = (String) response.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();

        Claims claims = jwtService.validateAndExtract(accessToken);
        assertThat(jwtService.extractTenantId(claims)).isNull();
        assertThat(jwtService.extractRole(claims)).isEqualTo("SUPER_ADMIN");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Refresh token tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void refresh_validCookie_rotatesToken() {
        String rawToken1 = loginAndGetRefreshToken(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG);

        ResponseEntity<Map> refreshResponse = refreshWithCookie(rawToken1);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).containsKey("accessToken");

        String rawToken2 = extractCookieValue(
                refreshResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "refresh_token");
        assertThat(rawToken2).isNotNull().isNotEqualTo(rawToken1);

        // Old token must be rejected
        assertThat(refreshWithCookie(rawToken1).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_revokedToken_revokesFamily_returns401() {
        String rawToken1 = loginAndGetRefreshToken(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG);
        // Rotate — rawToken1 becomes revoked
        refreshWithCookie(rawToken1);

        // Present revoked token → family revocation + 401
        assertThat(refreshWithCookie(rawToken1).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify entire family is revoked in DB
        com.cenicast.lis.auth.model.RefreshToken stored = findTokenByRawValue(rawToken1);
        if (stored != null) {
            List<com.cenicast.lis.auth.model.RefreshToken> family = refreshTokenRepository.findAll()
                    .stream().filter(t -> t.getFamilyId().equals(stored.getFamilyId())).toList();
            assertThat(family).isNotEmpty().allMatch(com.cenicast.lis.auth.model.RefreshToken::isRevoked);
        }
    }

    @Test
    void refresh_expiredToken_returns401() {
        User userA = userRepository.findByEmailAndTenantId(LAB_A_EMAIL, tenantAId).orElseThrow();
        String rawToken = UUID.randomUUID().toString();

        com.cenicast.lis.auth.model.RefreshToken expired = new com.cenicast.lis.auth.model.RefreshToken();
        expired.setTokenHash(sha256Hex(rawToken));
        expired.setUserId(userA.getId());
        expired.setTenantId(tenantAId);
        expired.setFamilyId(UUID.randomUUID());
        expired.setExpiresAt(Instant.now().minusSeconds(3600));
        expired.setRevoked(false);
        refreshTokenRepository.save(expired);

        assertThat(refreshWithCookie(rawToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Expired token (not reused) must NOT trigger family revocation
        com.cenicast.lis.auth.model.RefreshToken stored = refreshTokenRepository
                .findByTokenHash(sha256Hex(rawToken)).orElseThrow();
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void logout_clearsSession() {
        ResponseEntity<Map> loginResp = rest.postForEntity(
                "/api/v1/auth/login", loginBody(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG), Map.class);
        String accessToken = (String) loginResp.getBody().get("accessToken");
        String rawToken = extractCookieValue(
                loginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set(HttpHeaders.COOKIE, "refresh_token=" + rawToken);
        ResponseEntity<Void> logoutResp = rest.exchange(
                "/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(refreshWithCookie(rawToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tenant isolation tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void tenantA_cannotSeeTenantsB_users() {
        String tokenA = loginAndGetAccessToken(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokenA);
        ResponseEntity<Map> response = rest.exchange("/api/v1/users", HttpMethod.GET, new HttpEntity<>(h), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        content.forEach(obj -> {
            Map<?, ?> user = (Map<?, ?>) obj;
            assertThat(user.get("tenantId").toString()).isNotEqualTo(tenantBId.toString());
        });
    }

    @Test
    void superAdmin_cannotCallUsersEndpoint_returns403() {
        String token = loginAndGetAccessToken(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD, null);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        ResponseEntity<Map> response = rest.exchange("/api/v1/users", HttpMethod.GET, new HttpEntity<>(h), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantA_cannotGetTenantB_userById_returns404() {
        String tokenA = loginAndGetAccessToken(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokenA);
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/users/" + userBId, HttpMethod.GET, new HttpEntity<>(h), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Audit tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void auditEvent_failedLogin_persistedInDb() {
        long before = auditEventRepository.count();
        rest.postForEntity("/api/v1/auth/login",
                loginBody(LAB_A_EMAIL, "BadPassword!!", LAB_A_SLUG), Map.class);

        assertThat(auditEventRepository.count()).isGreaterThan(before);
        assertThat(auditEventRepository.findAll())
                .anyMatch(e -> "FAILED_LOGIN".equals(e.getAction()));
    }

    @Test
    void auditEvent_login_persistedInDb() {
        long before = auditEventRepository.count();
        rest.postForEntity("/api/v1/auth/login",
                loginBody(LAB_A_EMAIL, LAB_A_PASSWORD, LAB_A_SLUG), Map.class);

        assertThat(auditEventRepository.count()).isGreaterThan(before);
        assertThat(auditEventRepository.findAll())
                .anyMatch(e -> "LOGIN".equals(e.getAction()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String loginAndGetAccessToken(String email, String password, String tenantSlug) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", loginBody(email, password, tenantSlug), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private String loginAndGetRefreshToken(String email, String password, String tenantSlug) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", loginBody(email, password, tenantSlug), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extractCookieValue(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "refresh_token");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> refreshWithCookie(String rawToken) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.COOKIE, "refresh_token=" + rawToken);
        return rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(h), Map.class);
    }

    private Map<String, Object> loginBody(String email, String password, String tenantSlug) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (tenantSlug != null) body.put("tenantSlug", tenantSlug);
        return body;
    }

    private String extractCookieValue(String setCookieHeader, String name) {
        if (setCookieHeader == null) return null;
        for (String part : setCookieHeader.split(";")) {
            part = part.trim();
            if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        }
        return null;
    }

    private com.cenicast.lis.auth.model.RefreshToken findTokenByRawValue(String raw) {
        return refreshTokenRepository.findByTokenHash(sha256Hex(raw)).orElse(null);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
