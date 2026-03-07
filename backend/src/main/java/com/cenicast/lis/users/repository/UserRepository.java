package com.cenicast.lis.users.repository;

import com.cenicast.lis.users.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    /** SUPER_ADMIN login path — matches the row where tenant_id IS NULL. */
    Optional<User> findByEmailAndTenantIdIsNull(String email);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    /**
     * JPQL-based findById so the Hibernate @Filter applies.
     * EntityManager.find() (used by the inherited findById) bypasses session filters;
     * JPQL queries respect them.
     */
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithFilter(@Param("id") UUID id);
}
