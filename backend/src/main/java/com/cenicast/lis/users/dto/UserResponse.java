package com.cenicast.lis.users.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean active,
        Instant createdAt
) {}
