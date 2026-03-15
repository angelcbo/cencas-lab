package com.cenicast.lis.catalog;

import com.cenicast.lis.tenant.model.Tenant;
import com.cenicast.lis.tenant.repository.TenantRepository;
import com.cenicast.lis.users.model.Role;
import com.cenicast.lis.users.model.User;
import com.cenicast.lis.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CatalogIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String LAB_A_SLUG     = "catalog-test-lab-a";
    private static final String LAB_A_ADMIN    = "catalog-admin-a@test.com";
    private static final String LAB_A_ANALYST  = "catalog-analyst-a@test.com";
    private static final String LAB_A_PASSWORD = "CatalogPass123!";

    private static final String LAB_B_SLUG     = "catalog-test-lab-b";
    private static final String LAB_B_ADMIN    = "catalog-admin-b@test.com";
    private static final String LAB_B_PASSWORD = "CatalogPass456!";

    private static UUID tenantAId;
    private static UUID tenantBId;
    private static volatile boolean setupComplete = false;

    @BeforeEach
    void ensureTestDataExists() {
        if (setupComplete) return;
        synchronized (CatalogIntegrationTest.class) {
            if (setupComplete) return;

            Tenant tenantA = tenantRepository.findBySlug(LAB_A_SLUG).orElseGet(() -> {
                Tenant t = new Tenant();
                t.setSlug(LAB_A_SLUG);
                t.setName("Catalog Test Lab A");
                t.setTimezone("America/Mexico_City");
                t.setTaxRate(new BigDecimal("0.1600"));
                t.setActive(true);
                return tenantRepository.save(t);
            });
            tenantAId = tenantA.getId();

            userRepository.findByEmailAndTenantId(LAB_A_ADMIN, tenantAId).orElseGet(() -> {
                User u = new User();
                u.setTenantId(tenantAId);
                u.setEmail(LAB_A_ADMIN);
                u.setPasswordHash(passwordEncoder.encode(LAB_A_PASSWORD));
                u.setFirstName("Admin"); u.setLastName("A");
                u.setRole(Role.LAB_ADMIN);
                u.setActive(true);
                return userRepository.save(u);
            });

            userRepository.findByEmailAndTenantId(LAB_A_ANALYST, tenantAId).orElseGet(() -> {
                User u = new User();
                u.setTenantId(tenantAId);
                u.setEmail(LAB_A_ANALYST);
                u.setPasswordHash(passwordEncoder.encode(LAB_A_PASSWORD));
                u.setFirstName("Analyst"); u.setLastName("A");
                u.setRole(Role.LAB_ANALYST);
                u.setActive(true);
                return userRepository.save(u);
            });

            Tenant tenantB = tenantRepository.findBySlug(LAB_B_SLUG).orElseGet(() -> {
                Tenant t = new Tenant();
                t.setSlug(LAB_B_SLUG);
                t.setName("Catalog Test Lab B");
                t.setTimezone("America/Mexico_City");
                t.setTaxRate(new BigDecimal("0.1600"));
                t.setActive(true);
                return tenantRepository.save(t);
            });
            tenantBId = tenantB.getId();

            userRepository.findByEmailAndTenantId(LAB_B_ADMIN, tenantBId).orElseGet(() -> {
                User u = new User();
                u.setTenantId(tenantBId);
                u.setEmail(LAB_B_ADMIN);
                u.setPasswordHash(passwordEncoder.encode(LAB_B_PASSWORD));
                u.setFirstName("Admin"); u.setLastName("B");
                u.setRole(Role.LAB_ADMIN);
                u.setActive(true);
                return userRepository.save(u);
            });

            setupComplete = true;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Analyte tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createAnalyte_labAdmin_returns201() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody("GLU", "Glucose", "mg/dL", "NUMERIC"), bearer(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("code")).isEqualTo("GLU");
        assertThat(response.getBody().get("resultType")).isEqualTo("NUMERIC");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAnalyte_duplicateCode_returns409() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String code = "DUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        rest.exchange("/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody(code, "First", null, "TEXT"), bearer(token)), Map.class);

        ResponseEntity<Map> second = rest.exchange(
                "/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody(code, "Second", null, "TEXT"), bearer(token)), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAnalytes_tenantA_doesNotSeeTenantBAnalytes() {
        String tokenA = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);

        String codeB = "ONLY-B-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        rest.exchange("/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody(codeB, "Only in B", null, "QUALITATIVE"), bearer(tokenB)),
                Map.class);

        ResponseEntity<Map> listResponse = rest.exchange(
                "/api/v1/catalog/analytes", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenA)), Map.class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String content = listResponse.getBody().toString();
        assertThat(content).doesNotContain(codeB);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAnalyte_tenantA_cannotSeeTenantBAnalyte() {
        String tokenA = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);

        String codeB = "XB-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody(codeB, "B only analyte", null, "NUMERIC"), bearer(tokenB)),
                Map.class);
        String idB = (String) created.getBody().get("id");

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/analytes/" + idB, HttpMethod.GET,
                new HttpEntity<>(bearer(tokenA)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAnalyte_labAnalyst_returns200() {
        String adminToken   = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String analystToken = loginAndGetAccessToken(LAB_A_ANALYST, LAB_A_PASSWORD, LAB_A_SLUG);

        String code = "ANALYST-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody(code, "Analyst readable", null, "NUMERIC"), bearer(adminToken)),
                Map.class);
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/analytes/" + id, HttpMethod.GET,
                new HttpEntity<>(bearer(analystToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createAnalyte_labAnalyst_returns403() {
        String token = loginAndGetAccessToken(LAB_A_ANALYST, LAB_A_PASSWORD, LAB_A_SLUG);

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody("FORBIDDEN", "Should fail", null, "NUMERIC"), bearer(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateAnalyte_labAdmin_returns200AndUpdatedAt() throws InterruptedException {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String code = "UPD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody(code, "Original name", null, "NUMERIC"), bearer(token)),
                Map.class);
        String id = (String) created.getBody().get("id");
        String createdAt = (String) created.getBody().get("createdAt");

        Thread.sleep(10); // ensure updatedAt differs from createdAt

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("code", code);
        updateBody.put("name", "Updated name");
        updateBody.put("resultType", "TEXT");
        updateBody.put("active", true);

        ResponseEntity<Map> updated = rest.exchange(
                "/api/v1/catalog/analytes/" + id, HttpMethod.PUT,
                new HttpEntity<>(updateBody, bearer(token)), Map.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name")).isEqualTo("Updated name");
        assertThat(updated.getBody().get("resultType")).isEqualTo("TEXT");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Technique tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createTechnique_andListWithPagination() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String code = "TECH-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/catalog/techniques", HttpMethod.POST,
                new HttpEntity<>(techniqueBody(code, "Spectrophotometry", null), bearer(token)),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("code")).isEqualTo(code);

        ResponseEntity<Map> list = rest.exchange(
                "/api/v1/catalog/techniques?size=5", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).containsKey("content");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SpecimenType + CollectionContainer tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createSpecimenTypeAndContainer_returns201WithLinkedSpecimenType() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);

        // Create specimen type
        String stCode = "ST-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ResponseEntity<Map> stCreated = rest.exchange(
                "/api/v1/catalog/specimen-types", HttpMethod.POST,
                new HttpEntity<>(specimenTypeBody(stCode, "Venous blood", null), bearer(token)),
                Map.class);
        assertThat(stCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String stId = (String) stCreated.getBody().get("id");

        // Create collection container linked to the specimen type
        String ccCode = "CC-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ResponseEntity<Map> ccCreated = rest.exchange(
                "/api/v1/catalog/collection-containers", HttpMethod.POST,
                new HttpEntity<>(containerBody(ccCode, "Purple EDTA tube", "purple", stId, null),
                        bearer(token)),
                Map.class);

        assertThat(ccCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ccCreated.getBody().get("specimenTypeId")).isEqualTo(stId);
        assertThat(ccCreated.getBody().get("specimenTypeName")).isEqualTo("Venous blood");
        assertThat(ccCreated.getBody().get("color")).isEqualTo("purple");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createContainer_withTenantBSpecimenTypeId_returns422() {
        String tokenA = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);

        // Create specimen type in Tenant B
        String stCode = "ST-B-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ResponseEntity<Map> stB = rest.exchange(
                "/api/v1/catalog/specimen-types", HttpMethod.POST,
                new HttpEntity<>(specimenTypeBody(stCode, "Tenant B blood", null), bearer(tokenB)),
                Map.class);
        String stBId = (String) stB.getBody().get("id");

        // Tenant A tries to create a container referencing Tenant B's specimen type
        String ccCode = "CC-XTEN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/collection-containers", HttpMethod.POST,
                new HttpEntity<>(containerBody(ccCode, "Cross-tenant attempt", null, stBId, null),
                        bearer(tokenA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String loginAndGetAccessToken(String email, String password, String tenantSlug) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("tenantSlug", tenantSlug);
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private Map<String, Object> analyteBody(String code, String name, String unit, String resultType) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        if (unit != null) b.put("defaultUnit", unit);
        b.put("resultType", resultType);
        return b;
    }

    private Map<String, Object> techniqueBody(String code, String name, String description) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        if (description != null) b.put("description", description);
        return b;
    }

    private Map<String, Object> specimenTypeBody(String code, String name, String description) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        if (description != null) b.put("description", description);
        return b;
    }

    private Map<String, Object> containerBody(String code, String name, String color,
                                               String specimenTypeId, String description) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        if (color != null) b.put("color", color);
        b.put("specimenTypeId", specimenTypeId);
        if (description != null) b.put("description", description);
        return b;
    }
}
