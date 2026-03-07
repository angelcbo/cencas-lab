package com.cenicast.lis.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enables the Hibernate tenant filter before any Spring Data repository method call.
 *
 * Why this pointcut is safe:
 * - Repository methods are always called from inside @Transactional service methods.
 * - When @Before fires, the caller's Hibernate Session is already open.
 * - @PersistenceContext EntityManager is a thread-bound proxy — unwrap(Session.class)
 *   returns the active session for the current thread/transaction.
 * - enableFilter() on an already-enabled filter is idempotent in Hibernate 6.
 */
@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.cenicast.lis..*Repository.*(..))")
    public void enableTenantFilter() {
        UUID tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            // SUPER_ADMIN or unauthenticated — filter not enabled.
            // SUPER_ADMIN is blocked from tenant-scoped endpoints by @PreAuthorize anyway;
            // this branch only fires for platform endpoints querying non-tenant-scoped entities.
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }
}
