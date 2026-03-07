package com.cenicast.lis.auth.service;

import com.cenicast.lis.auth.dto.LoginRequest;
import com.cenicast.lis.auth.dto.UserInfo;
import com.cenicast.lis.auth.model.RefreshToken;
import com.cenicast.lis.auth.repository.RefreshTokenRepository;
import com.cenicast.lis.common.audit.AuditAction;
import com.cenicast.lis.common.audit.AuditService;
import com.cenicast.lis.common.exception.ApiException;
import com.cenicast.lis.common.security.JwtService;
import com.cenicast.lis.common.security.UserPrincipal;
import com.cenicast.lis.tenant.repository.TenantRepository;
import com.cenicast.lis.users.model.User;
import com.cenicast.lis.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class AuthService {

    /** Returned from login() — carries raw refresh token (never persisted as-is; hashed in DB). */
    public record LoginResult(String accessToken, String rawRefreshToken, UserInfo user) {}

    /** Returned from refresh() — carries the new raw refresh token for the cookie. */
    public record RefreshResult(String accessToken, String rawRefreshToken) {}

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${app.jwt.refresh-token-expiry-days:30}")
    private int refreshTokenExpiryDays;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResult login(LoginRequest req, String ipAddress) {
        // Resolve user by path: SUPER_ADMIN (null slug) vs lab user (tenant slug)
        User user;
        if (req.tenantSlug() == null) {
            user = userRepository.findByEmailAndTenantIdIsNull(req.email())
                    .orElse(null);
        } else {
            var tenant = tenantRepository.findBySlug(req.tenantSlug())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));
            user = userRepository.findByEmailAndTenantId(req.email(), tenant.getId())
                    .orElse(null);
        }

        // Wrong email or password — always audit even if user not found.
        // actor_id must be non-null per schema; use a sentinel UUID when the user doesn't exist.
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            UUID actorId = user != null ? user.getId() : new UUID(0, 0);
            UUID tenantId = user != null ? user.getTenantId() : null;
            String email = user != null ? user.getEmail() : req.email();
            auditService.recordAuth(AuditAction.FAILED_LOGIN, actorId, email, tenantId, ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Account is inactive");
        }

        // Issue refresh token (opaque UUID, stored as SHA-256 hash)
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setUserId(user.getId());
        refreshToken.setTenantId(user.getTenantId());
        refreshToken.setFamilyId(UUID.randomUUID());
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        refreshToken.setIpAddress(ipAddress);
        refreshTokenRepository.save(refreshToken);

        UserPrincipal principal = User.toPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);

        auditService.recordAuth(AuditAction.LOGIN, user.getId(), user.getEmail(), user.getTenantId(), ipAddress);

        UserInfo userInfo = new UserInfo(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole().name(), user.getTenantId());
        return new LoginResult(accessToken, rawToken, userInfo);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RefreshResult refresh(String rawCookieToken, String ipAddress) {
        if (rawCookieToken == null || rawCookieToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }

        String hash = sha256Hex(rawCookieToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        // Reuse detection: revoked token presented → revoke entire family
        if (existing.isRevoked()) {
            refreshTokenRepository.revokeFamily(existing.getFamilyId());
            // Load user email for the audit trail — at refresh time, filter is not active
            String victimEmail = userRepository.findById(existing.getUserId())
                    .map(User::getEmail).orElse("unknown");
            auditService.recordAuth(AuditAction.TOKEN_REVOKED,
                    existing.getUserId(), victimEmail, existing.getTenantId(), ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Refresh token reuse detected — all sessions invalidated");
        }

        if (Instant.now().isAfter(existing.getExpiresAt())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        // Rotate: mark old token revoked, issue new one (same family)
        existing.setRevoked(true);

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        String newRaw = UUID.randomUUID().toString();
        String newHash = sha256Hex(newRaw);

        RefreshToken newToken = new RefreshToken();
        newToken.setTokenHash(newHash);
        newToken.setUserId(user.getId());
        newToken.setTenantId(user.getTenantId());
        newToken.setFamilyId(existing.getFamilyId());
        newToken.setExpiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        newToken.setRevoked(false);
        newToken.setIpAddress(ipAddress);
        newToken = refreshTokenRepository.save(newToken);

        existing.setReplacedBy(newToken.getId());
        refreshTokenRepository.save(existing);

        UserPrincipal principal = User.toPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);

        auditService.recordAuth(AuditAction.TOKEN_REFRESH,
                user.getId(), user.getEmail(), user.getTenantId(), ipAddress);

        return new RefreshResult(accessToken, newRaw);
    }

    @Transactional
    public void logout(String rawCookieToken, UserPrincipal principal, String ipAddress) {
        if (rawCookieToken == null || rawCookieToken.isBlank()) {
            auditService.recordAuth(AuditAction.LOGOUT,
                    principal.getUserId(), principal.getEmail(), principal.getTenantId(), ipAddress);
            return;
        }

        String hash = sha256Hex(rawCookieToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            }
        });

        auditService.recordAuth(AuditAction.LOGOUT,
                principal.getUserId(), principal.getEmail(), principal.getTenantId(), ipAddress);
    }

    /** SHA-256 hex digest using Java 17 built-ins — no external library. */
    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in any Java runtime — this never happens
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
