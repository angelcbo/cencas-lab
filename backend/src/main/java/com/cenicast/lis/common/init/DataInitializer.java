package com.cenicast.lis.common.init;

import com.cenicast.lis.tenant.model.Tenant;
import com.cenicast.lis.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import com.cenicast.lis.users.model.Role;
import com.cenicast.lis.users.model.User;
import com.cenicast.lis.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps dev/test accounts on startup. Never runs in production (@Profile("!prod")).
 * All operations are idempotent — safe to run repeatedly.
 * Override credentials via app.init.* in application-dev.yml or environment variables.
 */
@Component
@Profile("!prod")
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.super-admin-email:admin@cenicast.com}")
    private String superAdminEmail;

    @Value("${app.init.super-admin-password:ChangeMe123!}")
    private String superAdminPassword;

    @Value("${app.init.demo-tenant-slug:demo-lab}")
    private String demoTenantSlug;

    @Value("${app.init.demo-tenant-name:Demo Lab}")
    private String demoTenantName;

    @Value("${app.init.lab-admin-email:labadmin@demo.com}")
    private String labAdminEmail;

    @Value("${app.init.lab-admin-password:ChangeMe123!}")
    private String labAdminPassword;

    public DataInitializer(UserRepository userRepository,
                           TenantRepository tenantRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrapSuperAdmin();
        bootstrapDemoTenant();
    }

    private void bootstrapSuperAdmin() {
        if (userRepository.findByEmailAndTenantIdIsNull(superAdminEmail).isPresent()) {
            return;
        }
        User admin = new User();
        admin.setEmail(superAdminEmail);
        admin.setPasswordHash(passwordEncoder.encode(superAdminPassword));
        admin.setFirstName("Super");
        admin.setLastName("Admin");
        admin.setRole(Role.SUPER_ADMIN);
        admin.setActive(true);
        // tenantId intentionally null — SUPER_ADMIN is platform-level
        userRepository.save(admin);
        log.info("SUPER_ADMIN bootstrapped: {}", superAdminEmail);
    }

    private void bootstrapDemoTenant() {
        if (tenantRepository.existsBySlug(demoTenantSlug)) {
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setSlug(demoTenantSlug);
        tenant.setName(demoTenantName);
        tenant.setTimezone("America/Mexico_City");
        tenant.setTaxRate(new BigDecimal("0.1600"));
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);

        User labAdmin = new User();
        labAdmin.setTenantId(tenant.getId());
        labAdmin.setEmail(labAdminEmail);
        labAdmin.setPasswordHash(passwordEncoder.encode(labAdminPassword));
        labAdmin.setFirstName("Lab");
        labAdmin.setLastName("Admin");
        labAdmin.setRole(Role.LAB_ADMIN);
        labAdmin.setActive(true);
        userRepository.save(labAdmin);

        log.info("Demo tenant bootstrapped: {} (slug={})", demoTenantName, demoTenantSlug);
    }
}
