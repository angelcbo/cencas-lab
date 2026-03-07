package com.cenicast.lis.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,   // seconds — always 900 (15 minutes)
        UserInfo user
) {}
