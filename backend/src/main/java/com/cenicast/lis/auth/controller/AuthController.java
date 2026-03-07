package com.cenicast.lis.auth.controller;

import com.cenicast.lis.auth.dto.LoginRequest;
import com.cenicast.lis.auth.dto.LoginResponse;
import com.cenicast.lis.auth.dto.RefreshResponse;
import com.cenicast.lis.auth.service.AuthService;
import com.cenicast.lis.auth.service.AuthService.LoginResult;
import com.cenicast.lis.auth.service.AuthService.RefreshResult;
import com.cenicast.lis.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 900L; // 15 minutes
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);

    private final AuthService authService;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookieDomain;

    public AuthController(
            AuthService authService,
            @Value("${app.cookie.secure}") boolean cookieSecure,
            @Value("${app.cookie.same-site}") String cookieSameSite,
            @Value("${app.cookie.domain}") String cookieDomain) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieDomain = cookieDomain;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpRequest) {

        LoginResult result = authService.login(req, getClientIp(httpRequest));
        ResponseCookie cookie = buildCookie(result.rawRefreshToken(), COOKIE_MAX_AGE);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(result.accessToken(), "Bearer",
                        ACCESS_TOKEN_EXPIRY_SECONDS, result.user()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String rawToken,
            HttpServletRequest httpRequest) {

        RefreshResult result = authService.refresh(rawToken, getClientIp(httpRequest));
        ResponseCookie cookie = buildCookie(result.rawRefreshToken(), COOKIE_MAX_AGE);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new RefreshResponse(result.accessToken(), "Bearer",
                        ACCESS_TOKEN_EXPIRY_SECONDS));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String rawToken,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        authService.logout(rawToken, principal, getClientIp(httpRequest));
        ResponseCookie clearCookie = buildCookie("", Duration.ZERO);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .domain(cookieDomain)
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
