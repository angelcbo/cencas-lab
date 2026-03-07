package com.cenicast.lis.users.dto;

import com.cenicast.lis.users.model.Role;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        Role role,
        Boolean active
) {}
