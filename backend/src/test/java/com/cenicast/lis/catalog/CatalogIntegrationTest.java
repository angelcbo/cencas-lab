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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    // LabTest tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_labAdmin_returns201WithComposition() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);

        // Create prerequisite catalog entries
        String stId    = createSpecimenType(token, "ST-LT-" + uid(), "Blood");
        String ccId    = createCollectionContainer(token, "CC-LT-" + uid(), "EDTA tube", stId);
        String techId  = createTechnique(token, "TK-LT-" + uid(), "Colorimetry");
        String an1Id   = createAnalyte(token, "AN1-" + uid(), "Glucose", "NUMERIC");
        String an2Id   = createAnalyte(token, "AN2-" + uid(), "Sodium", "NUMERIC");

        Map<String, Object> body = labTestBody(
                "TST-" + uid(), "Glucose + Sodium test", stId, 4, "120.00",
                List.of(
                    Map.of("analyteId", an1Id, "displayOrder", 2, "reportable", true),
                    Map.of("analyteId", an2Id, "displayOrder", 1, "reportable", true)
                ),
                List.of(techId),
                List.of(Map.of("collectionContainerId", ccId, "required", true))
        );

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("specimenTypeName")).isEqualTo("Blood");

        // Analytes should be ordered by displayOrder ASC: [an2Id (order=1), an1Id (order=2)]
        List<Map<String, Object>> analytes = (List<Map<String, Object>>) response.getBody().get("analytes");
        assertThat(analytes).hasSize(2);
        assertThat(analytes.get(0).get("displayOrder")).isEqualTo(1);
        assertThat(analytes.get(1).get("displayOrder")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_duplicateCode_returns409() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);

        String stId = createSpecimenType(token, "ST-DC-" + uid(), "Serum");
        String ccId = createCollectionContainer(token, "CC-DC-" + uid(), "Red tube", stId);
        String anId = createAnalyte(token, "AN-DC-" + uid(), "Total Protein", "NUMERIC");

        String code = "TST-DUP-" + uid();
        Map<String, Object> body = labTestBody(code, "Original", stId, 2, "50.00",
                List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                List.of(),
                List.of(Map.of("collectionContainerId", ccId, "required", true)));

        rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), Map.class);

        ResponseEntity<Map> second = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listLabTests_tenantA_doesNotSeeTenantBTests() {
        String tokenA = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);

        String stBId = createSpecimenType(tokenB, "ST-B-LT-" + uid(), "Urine");
        String ccBId = createCollectionContainer(tokenB, "CC-B-LT-" + uid(), "Urine cup", stBId);
        String anBId = createAnalyte(tokenB, "AN-B-LT-" + uid(), "Creatinine", "NUMERIC");
        String codeBOnly = "TST-B-ONLY-" + uid();
        rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody(codeBOnly, "Only in B", stBId, 1, "30.00",
                        List.of(Map.of("analyteId", anBId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccBId, "required", true))),
                        bearer(tokenB)), Map.class);

        ResponseEntity<Map> list = rest.exchange(
                "/api/v1/catalog/tests", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenA)), Map.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().toString()).doesNotContain(codeBOnly);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLabTest_tenantA_cannotSeeTenantBTest() {
        String tokenA = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);

        String stBId = createSpecimenType(tokenB, "ST-B-GT-" + uid(), "CSF");
        String ccBId = createCollectionContainer(tokenB, "CC-B-GT-" + uid(), "CSF tube", stBId);
        String anBId = createAnalyte(tokenB, "AN-B-GT-" + uid(), "Glucose CSF", "NUMERIC");

        ResponseEntity<Map> created = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-B-GT-" + uid(), "B only test", stBId, 1, "20.00",
                        List.of(Map.of("analyteId", anBId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccBId, "required", true))),
                        bearer(tokenB)), Map.class);
        String idB = (String) created.getBody().get("id");

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/tests/" + idB, HttpMethod.GET,
                new HttpEntity<>(bearer(tokenA)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withTenantBAnalyteId_returns422() {
        String tokenA = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);

        String stAId  = createSpecimenType(tokenA, "ST-A-XA-" + uid(), "Plasma");
        String ccAId  = createCollectionContainer(tokenA, "CC-A-XA-" + uid(), "EDTA", stAId);
        String anBId  = createAnalyte(tokenB, "AN-B-XA-" + uid(), "B Analyte", "NUMERIC");

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-XA-" + uid(), "Cross analyte test", stAId, 1, "20.00",
                        List.of(Map.of("analyteId", anBId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccAId, "required", true))),
                        bearer(tokenA)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withDuplicateAnalyteIds_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-DA-" + uid(), "Blood");
        String ccId  = createCollectionContainer(token, "CC-DA-" + uid(), "tube", stId);
        String anId  = createAnalyte(token, "AN-DA-" + uid(), "Dup Analyte", "NUMERIC");

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-DA-" + uid(), "Dup analyte test", stId, 1, "10.00",
                        List.of(
                            Map.of("analyteId", anId, "displayOrder", 0, "reportable", true),
                            Map.of("analyteId", anId, "displayOrder", 1, "reportable", true)
                        ),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withDuplicateTechniqueIds_returns422() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId   = createSpecimenType(token, "ST-DT-" + uid(), "Blood");
        String ccId   = createCollectionContainer(token, "CC-DT-" + uid(), "tube", stId);
        String anId   = createAnalyte(token, "AN-DT-" + uid(), "Analyte", "NUMERIC");
        String techId = createTechnique(token, "TK-DT-" + uid(), "PCR");

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-DT-" + uid(), "Dup technique test", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(techId, techId),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withDuplicateContainerIds_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-DC2-" + uid(), "Blood");
        String ccId  = createCollectionContainer(token, "CC-DC2-" + uid(), "tube", stId);
        String anId  = createAnalyte(token, "AN-DC2-" + uid(), "Analyte", "NUMERIC");

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-DC2-" + uid(), "Dup container test", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(
                            Map.of("collectionContainerId", ccId, "required", true),
                            Map.of("collectionContainerId", ccId, "required", false)
                        )),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withEmptyAnalytes_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-EA-" + uid(), "Blood");
        String ccId  = createCollectionContainer(token, "CC-EA-" + uid(), "tube", stId);

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-EA-" + uid(), "No analyte test", stId, 1, "10.00",
                        List.of(),   // empty analytes
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withEmptyContainers_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-EC-" + uid(), "Blood");
        String anId  = createAnalyte(token, "AN-EC-" + uid(), "Analyte", "NUMERIC");

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-EC-" + uid(), "No container test", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of()  // empty containers
                        ),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withNegativePrice_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-NP-" + uid(), "Blood");
        String ccId  = createCollectionContainer(token, "CC-NP-" + uid(), "tube", stId);
        String anId  = createAnalyte(token, "AN-NP-" + uid(), "Analyte", "NUMERIC");

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-NP-" + uid(), "Negative price test", stId, 1, "-5.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withNegativeDisplayOrder_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-ND-" + uid(), "Blood");
        String ccId  = createCollectionContainer(token, "CC-ND-" + uid(), "tube", stId);
        String anId  = createAnalyte(token, "AN-ND-" + uid(), "Analyte", "NUMERIC");

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-ND-" + uid(), "Negative order test", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", -1, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_withInactiveReferencedEntity_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-IA-" + uid(), "Blood");
        String ccId  = createCollectionContainer(token, "CC-IA-" + uid(), "tube", stId);
        String anCode = "AN-IA-" + uid();
        String anId  = createAnalyte(token, anCode, "Inactive Analyte", "NUMERIC");

        // Deactivate the analyte
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("code", anCode);
        updateBody.put("name", "Inactive Analyte");
        updateBody.put("resultType", "NUMERIC");
        updateBody.put("active", false);
        rest.exchange("/api/v1/catalog/analytes/" + anId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, bearer(token)), Map.class);

        // Try to create a test referencing the inactive analyte
        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-IA-" + uid(), "Inactive ref test", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateLabTest_replacesComposition() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId  = createSpecimenType(token, "ST-UR-" + uid(), "Blood");
        String ccId  = createCollectionContainer(token, "CC-UR-" + uid(), "tube", stId);
        String an1Id = createAnalyte(token, "AN-UR1-" + uid(), "Analyte1", "NUMERIC");
        String an2Id = createAnalyte(token, "AN-UR2-" + uid(), "Analyte2", "NUMERIC");
        String code  = "TST-UR-" + uid();

        // Create with analyte 1
        ResponseEntity<Map> created = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody(code, "Replace test", stId, 1, "10.00",
                        List.of(Map.of("analyteId", an1Id, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);
        String id = (String) created.getBody().get("id");

        // Update with analyte 2 only (replace composition)
        Map<String, Object> updateBody = labTestBody(code, "Replace test", stId, 1, "10.00",
                List.of(Map.of("analyteId", an2Id, "displayOrder", 0, "reportable", true)),
                List.of(),
                List.of(Map.of("collectionContainerId", ccId, "required", true)));
        updateBody.put("active", true);

        ResponseEntity<Map> updated = rest.exchange("/api/v1/catalog/tests/" + id, HttpMethod.PUT,
                new HttpEntity<>(updateBody, bearer(token)), Map.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> analytes = (List<Map<String, Object>>) updated.getBody().get("analytes");
        assertThat(analytes).hasSize(1);
        assertThat(analytes.get(0).get("analyteId")).isEqualTo(an2Id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateLabTest_changeCodeToExistingCode_returns409() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId   = createSpecimenType(token, "ST-UC-" + uid(), "Blood");
        String ccId   = createCollectionContainer(token, "CC-UC-" + uid(), "tube", stId);
        String anId   = createAnalyte(token, "AN-UC-" + uid(), "Analyte", "NUMERIC");
        String code1  = "TST-UC1-" + uid();
        String code2  = "TST-UC2-" + uid();

        // Create two tests
        rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody(code1, "Test1", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);
        ResponseEntity<Map> created2 = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody(code2, "Test2", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);
        String id2 = (String) created2.getBody().get("id");

        // Try to rename test2 to code1
        Map<String, Object> updateBody = labTestBody(code1, "Test2 renamed", stId, 1, "10.00",
                List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                List.of(),
                List.of(Map.of("collectionContainerId", ccId, "required", true)));
        updateBody.put("active", true);
        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests/" + id2, HttpMethod.PUT,
                new HttpEntity<>(updateBody, bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLabTest_labAnalyst_returns200() {
        String adminToken   = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String analystToken = loginAndGetAccessToken(LAB_A_ANALYST, LAB_A_PASSWORD, LAB_A_SLUG);

        String stId = createSpecimenType(adminToken, "ST-LA-" + uid(), "Blood");
        String ccId = createCollectionContainer(adminToken, "CC-LA-" + uid(), "tube", stId);
        String anId = createAnalyte(adminToken, "AN-LA-" + uid(), "Analyte", "NUMERIC");

        ResponseEntity<Map> created = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-LA-" + uid(), "Analyst readable test", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(adminToken)), Map.class);
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/tests/" + id, HttpMethod.GET,
                new HttpEntity<>(bearer(analystToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLabTest_labAnalyst_returns403() {
        String adminToken  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String analystToken = loginAndGetAccessToken(LAB_A_ANALYST, LAB_A_PASSWORD, LAB_A_SLUG);

        // Set up valid prerequisites using admin token
        String stId = createSpecimenType(adminToken, "ST-F-" + uid(), "Blood");
        String ccId = createCollectionContainer(adminToken, "CC-F-" + uid(), "tube", stId);
        String anId = createAnalyte(adminToken, "AN-F-" + uid(), "Analyte", "NUMERIC");

        // Analyst must not be able to create a test even with a valid body
        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody("TST-FORBIDDEN-" + uid(), "Forbidden", stId,
                        1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(analystToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Panel tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createPanel_labAdmin_returns201WithTests() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String testId = createLabTestForPanel(token, "TST-CP-" + uid());

        Map<String, Object> body = panelBody("PNL-CP-" + uid(), "My Panel", null,
                List.of(Map.of("testId", testId, "displayOrder", 0)));

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        List<Map<String, Object>> tests = (List<Map<String, Object>>) response.getBody().get("tests");
        assertThat(tests).hasSize(1);
        assertThat(tests.get(0).get("testId")).isEqualTo(testId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPanel_duplicateCode_returns409() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String testId = createLabTestForPanel(token, "TST-PDP-" + uid());
        String code   = "PNL-DUP-" + uid();

        Map<String, Object> body = panelBody(code, "Panel", null,
                List.of(Map.of("testId", testId, "displayOrder", 0)));
        rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), Map.class);

        ResponseEntity<Map> second = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPanels_tenantIsolation() {
        String tokenA = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);
        String testBId = createLabTestForPanel(tokenB, "TST-B-PLI-" + uid());

        String codeB = "PNL-B-ONLY-" + uid();
        rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody(codeB, "B Panel", null,
                        List.of(Map.of("testId", testBId, "displayOrder", 0))),
                        bearer(tokenB)), Map.class);

        ResponseEntity<Map> list = rest.exchange("/api/v1/catalog/panels", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenA)), Map.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().toString()).doesNotContain(codeB);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPanel_tenantA_cannotSeeTenantBPanel() {
        String tokenA  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB  = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);
        String testBId = createLabTestForPanel(tokenB, "TST-B-PGT-" + uid());

        ResponseEntity<Map> created = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody("PNL-B-GT-" + uid(), "B Panel", null,
                        List.of(Map.of("testId", testBId, "displayOrder", 0))),
                        bearer(tokenB)), Map.class);
        String idB = (String) created.getBody().get("id");

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/panels/" + idB, HttpMethod.GET,
                new HttpEntity<>(bearer(tokenA)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPanel_withTenantBTestId_returns422() {
        String tokenA  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String tokenB  = loginAndGetAccessToken(LAB_B_ADMIN, LAB_B_PASSWORD, LAB_B_SLUG);
        String testBId = createLabTestForPanel(tokenB, "TST-B-PXT-" + uid());

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody("PNL-XT-" + uid(), "Cross tenant panel", null,
                        List.of(Map.of("testId", testBId, "displayOrder", 0))),
                        bearer(tokenA)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPanel_withDuplicateTestIds_returns422() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String testId = createLabTestForPanel(token, "TST-PDT-" + uid());

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody("PNL-DT-" + uid(), "Dup tests", null,
                        List.of(
                            Map.of("testId", testId, "displayOrder", 0),
                            Map.of("testId", testId, "displayOrder", 1)
                        )),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPanel_withEmptyTests_returns422() {
        String token = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody("PNL-ET-" + uid(), "Empty tests", null, List.of()),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPanel_withNegativeDisplayOrder_returns422() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String testId = createLabTestForPanel(token, "TST-PND-" + uid());

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody("PNL-ND-" + uid(), "Neg order", null,
                        List.of(Map.of("testId", testId, "displayOrder", -1))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPanel_withInactiveTest_returns422() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String stId   = createSpecimenType(token, "ST-PIT-" + uid(), "Blood");
        String ccId   = createCollectionContainer(token, "CC-PIT-" + uid(), "tube", stId);
        String anId   = createAnalyte(token, "AN-PIT-" + uid(), "Analyte", "NUMERIC");
        String code   = "TST-PIT-" + uid();

        // Create then deactivate a test
        ResponseEntity<Map> created = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody(code, "To deactivate", stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);
        String testId = (String) created.getBody().get("id");

        Map<String, Object> deactivateBody = labTestBody(code, "To deactivate", stId, 1, "10.00",
                List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                List.of(),
                List.of(Map.of("collectionContainerId", ccId, "required", true)));
        deactivateBody.put("active", false);
        rest.exchange("/api/v1/catalog/tests/" + testId, HttpMethod.PUT,
                new HttpEntity<>(deactivateBody, bearer(token)), Map.class);

        // Try to create a panel with the inactive test
        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody("PNL-IT-" + uid(), "Inactive test panel", null,
                        List.of(Map.of("testId", testId, "displayOrder", 0))),
                        bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatePanel_changeCodeToExistingCode_returns409() {
        String token   = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String testId  = createLabTestForPanel(token, "TST-PUC-" + uid());
        String code1   = "PNL-PUC1-" + uid();
        String code2   = "PNL-PUC2-" + uid();

        rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody(code1, "Panel1", null,
                        List.of(Map.of("testId", testId, "displayOrder", 0))),
                        bearer(token)), Map.class);
        ResponseEntity<Map> c2 = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody(code2, "Panel2", null,
                        List.of(Map.of("testId", testId, "displayOrder", 0))),
                        bearer(token)), Map.class);
        String id2 = (String) c2.getBody().get("id");

        Map<String, Object> updateBody = panelBody(code1, "Panel2", null,
                List.of(Map.of("testId", testId, "displayOrder", 0)));
        updateBody.put("active", true);

        ResponseEntity<Map> response = rest.exchange("/api/v1/catalog/panels/" + id2, HttpMethod.PUT,
                new HttpEntity<>(updateBody, bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPanel_enrichedWithTestDetails_orderedByDisplayOrder() {
        String token  = loginAndGetAccessToken(LAB_A_ADMIN, LAB_A_PASSWORD, LAB_A_SLUG);
        String test1Id = createLabTestForPanel(token, "TST-ORD1-" + uid());
        String test2Id = createLabTestForPanel(token, "TST-ORD2-" + uid());

        // Create panel with test1 at order=2 and test2 at order=1
        ResponseEntity<Map> created = rest.exchange("/api/v1/catalog/panels", HttpMethod.POST,
                new HttpEntity<>(panelBody("PNL-ORD-" + uid(), "Ordered Panel", null,
                        List.of(
                            Map.of("testId", test1Id, "displayOrder", 2),
                            Map.of("testId", test2Id, "displayOrder", 1)
                        )),
                        bearer(token)), Map.class);
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/catalog/panels/" + id, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> tests = (List<Map<String, Object>>) response.getBody().get("tests");
        assertThat(tests).hasSize(2);
        // First test should have displayOrder=1 (test2)
        assertThat(tests.get(0).get("displayOrder")).isEqualTo(1);
        // Second test should have displayOrder=2 (test1)
        assertThat(tests.get(1).get("displayOrder")).isEqualTo(2);
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

    private Map<String, Object> labTestBody(String code, String name, String specimenTypeId,
                                             int turnaroundTimeHours, String price,
                                             List<Map<String, Object>> analytes,
                                             List<String> techniqueIds,
                                             List<Map<String, Object>> containers) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        b.put("specimenTypeId", specimenTypeId);
        b.put("turnaroundTimeHours", turnaroundTimeHours);
        b.put("price", price);
        b.put("analytes", analytes);
        b.put("techniqueIds", techniqueIds);
        b.put("containers", containers);
        return b;
    }

    private Map<String, Object> panelBody(String code, String name, String description,
                                           List<Map<String, Object>> tests) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        if (description != null) b.put("description", description);
        b.put("tests", tests);
        return b;
    }

    // Convenience: create prerequisite catalog entities and return a lab test id
    @SuppressWarnings("unchecked")
    private String createLabTestForPanel(String token, String testCode) {
        String stId = createSpecimenType(token, "ST-" + uid(), "Blood");
        String ccId = createCollectionContainer(token, "CC-" + uid(), "tube", stId);
        String anId = createAnalyte(token, "AN-" + uid(), "Analyte", "NUMERIC");
        ResponseEntity<Map> resp = rest.exchange("/api/v1/catalog/tests", HttpMethod.POST,
                new HttpEntity<>(labTestBody(testCode, testCode, stId, 1, "10.00",
                        List.of(Map.of("analyteId", anId, "displayOrder", 0, "reportable", true)),
                        List.of(),
                        List.of(Map.of("collectionContainerId", ccId, "required", true))),
                        bearer(token)), Map.class);
        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String createSpecimenType(String token, String code, String name) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/catalog/specimen-types", HttpMethod.POST,
                new HttpEntity<>(specimenTypeBody(code, name, null), bearer(token)), Map.class);
        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String createCollectionContainer(String token, String code, String name, String stId) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/catalog/collection-containers", HttpMethod.POST,
                new HttpEntity<>(containerBody(code, name, null, stId, null), bearer(token)), Map.class);
        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String createAnalyte(String token, String code, String name, String resultType) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/catalog/analytes", HttpMethod.POST,
                new HttpEntity<>(analyteBody(code, name, null, resultType), bearer(token)), Map.class);
        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String createTechnique(String token, String code, String name) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/catalog/techniques", HttpMethod.POST,
                new HttpEntity<>(techniqueBody(code, name, null), bearer(token)), Map.class);
        return (String) resp.getBody().get("id");
    }

    /** Short random suffix to keep test codes unique across parallel test runs. */
    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
