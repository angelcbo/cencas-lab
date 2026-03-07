package com.cenicast.lis.common.security;

import java.util.UUID;

public class TenantContextHolder {

    private static final ThreadLocal<UUID> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(UUID tenantId) {
        CONTEXT.set(tenantId);
    }

    public static UUID get() {
        return CONTEXT.get();
    }

    /** Always call remove() — never set(null) — to avoid ThreadLocal leaks. */
    public static void clear() {
        CONTEXT.remove();
    }
}
