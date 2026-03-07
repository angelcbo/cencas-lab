package com.cenicast.lis.users.service;

import com.cenicast.lis.common.audit.AuditAction;
import com.cenicast.lis.common.audit.AuditService;
import com.cenicast.lis.common.exception.ApiException;
import com.cenicast.lis.common.security.UserPrincipal;
import com.cenicast.lis.users.dto.ChangePasswordRequest;
import com.cenicast.lis.users.dto.CreateUserRequest;
import com.cenicast.lis.users.dto.UpdateUserRequest;
import com.cenicast.lis.users.dto.UserResponse;
import com.cenicast.lis.users.model.Role;
import com.cenicast.lis.users.model.User;
import com.cenicast.lis.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * List users in the caller's tenant.
     * Hibernate filter (enabled by TenantFilterAspect) restricts results automatically.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        return userRepository.findByIdWithFilter(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest req, UUID tenantId,
                                   UserPrincipal actor, String ipAddress) {
        if (req.role() == Role.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot create SUPER_ADMIN via this endpoint");
        }
        if (userRepository.existsByEmailAndTenantId(req.email(), tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Email already in use within this tenant");
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setRole(req.role());
        user.setActive(true);
        user = userRepository.save(user);

        auditService.recordResource(AuditAction.CREATE_USER, actor,
                "User", user.getId(), Map.of("email", user.getEmail(), "role", user.getRole().name()), ipAddress);

        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest req,
                                   UserPrincipal actor, String ipAddress) {
        // JPQL-based lookup so the Hibernate @Filter applies — 404 if user not in caller's tenant
        User user = userRepository.findByIdWithFilter(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.role() != null) user.setRole(req.role());
        if (req.active() != null) user.setActive(req.active());
        user = userRepository.save(user);

        auditService.recordResource(AuditAction.UPDATE_USER, actor,
                "User", user.getId(), Map.of("role", user.getRole().name()), ipAddress);

        return toResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req,
                                UserPrincipal caller, String ipAddress) {
        // Enforce caller changes only their own password
        if (!userId.equals(caller.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cannot change another user's password");
        }

        User user = userRepository.findByIdWithFilter(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        auditService.recordAuth(AuditAction.PASSWORD_CHANGE,
                user.getId(), user.getEmail(), user.getTenantId(), ipAddress);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getTenantId(), u.getEmail(),
                u.getFirstName(), u.getLastName(), u.getRole().name(),
                u.isActive(), u.getCreatedAt());
    }
}
