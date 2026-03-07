package com.cenicast.lis.auth.dto;

import java.util.UUID;

public record UserInfo(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        UUID tenantId
) {}
