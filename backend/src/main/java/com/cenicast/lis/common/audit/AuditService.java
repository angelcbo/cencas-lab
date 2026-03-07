package com.cenicast.lis.common.audit;

import com.cenicast.lis.common.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Persists immutable audit events.
 *
 * REQUIRES_NEW propagation: each audit record commits in its own transaction,
 * so FAILED_LOGIN and other security events are persisted even if the calling
 * transaction rolls back.
 *
 * PHI policy: callers are responsible for omitting PHI from metadata.
 * Allowed: IDs, status transitions, role changes, token family IDs, counts.
 * Forbidden: patient name/DOB/CURP/phone/email, result values, passwords, tokens.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Core audit record method. Always commits in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuditAction action,
            UUID actorId,
            String actorEmail,
            UUID tenantId,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            String ipAddress) {

        AuditEvent event = new AuditEvent();
        event.setAction(action.name());
        event.setActorId(actorId);
        event.setActorEmail(actorEmail);
        event.setTenantId(tenantId);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setIpAddress(ipAddress);
        event.setCorrelationId(MDC.get("correlationId"));

        if (metadata != null) {
            try {
                event.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException e) {
                event.setMetadata("{\"error\":\"metadata_serialization_failed\"}");
            }
        }

        auditEventRepository.save(event);
    }

    /**
     * Convenience overload for auth events (no resource, no metadata).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuth(AuditAction action, UUID actorId, String email, UUID tenantId, String ip) {
        record(action, actorId, email, tenantId, null, null, null, ip);
    }

    /**
     * Convenience overload for resource events (with metadata).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResource(AuditAction action, UserPrincipal actor,
                               String resourceType, UUID resourceId,
                               Map<String, Object> meta, String ip) {
        record(action, actor.getUserId(), actor.getEmail(), actor.getTenantId(),
                resourceType, resourceId, meta, ip);
    }
}
