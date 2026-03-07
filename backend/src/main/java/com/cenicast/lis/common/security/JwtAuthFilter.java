package com.cenicast.lis.common.security;

import com.cenicast.lis.common.exception.ApiException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication filter.
 * Rebuilds UserPrincipal from signed JWT claims — no DB lookup per request.
 * Sets TenantContextHolder so the Hibernate tenant filter fires correctly.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // /logout requires authentication — only skip the unauthenticated auth endpoints
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.startsWith("/actuator/")
                || path.startsWith("/api/v1/docs")
                || path.startsWith("/api/v1/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Claims claims;
        try {
            claims = jwtService.validateAndExtract(token);
        } catch (ApiException e) {
            // Invalid token — let Spring Security reject the unauthenticated request
            chain.doFilter(request, response);
            return;
        }

        UserPrincipal principal = new UserPrincipal(
                jwtService.extractUserId(claims),
                jwtService.extractTenantId(claims),
                jwtService.extractEmail(claims),
                "",
                jwtService.extractRole(claims),
                true
        );

        TenantContextHolder.set(principal.getTenantId());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();  // must always run, even on exception
        }
    }
}
