# Catalog Module

The catalog is the foundational reference data that describes **what** a laboratory can test,
**how** it tests, and **what** it collects. All catalog entities are tenant-scoped — each lab
maintains its own catalog independently.

---

## Domain Glossary

### Analyte
The smallest measurable laboratory component.

An analyte represents a single measurable quantity (e.g. glucose, sodium, hemoglobin, TSH).
It carries a `resultType` that determines how results are entered and validated:

| ResultType | Meaning |
|---|---|
| `NUMERIC` | A numeric value with an optional unit (e.g. `5.4 mg/dL`). Auto-flagged against reference ranges in Sprint 5. |
| `TEXT` | Free-text observation (e.g. morphology description). No auto-flagging. |
| `QUALITATIVE` | Enumerated outcome such as Reactive / Non-reactive / Indeterminate. |

**Table:** `catalog_analytes`

---

### Technique
The analytical method used to perform a measurement.

A technique describes the instrument or reaction principle used to produce a result
(e.g. spectrophotometry, ELISA, PCR, nephelometry, flow cytometry). Techniques will be
referenced when defining studies (Sprint 3+) to record how a test was performed.

**Table:** `catalog_techniques`

---

### SpecimenType
The type of biological material required for a test.

A specimen type classifies the biological origin of the sample to be collected from the patient
(e.g. whole blood, serum, plasma, urine, CSF, swab). It determines what phlebotomy or collection
procedure is required.

**Table:** `catalog_specimen_types`

---

### CollectionContainer
The physical vessel used to collect a specimen.

A collection container is the tube, cup, or vial that holds the biological specimen
(e.g. purple EDTA tube, red serum tube, 24 h urine container, swab transport medium).
Every container is associated with exactly one `SpecimenType` — a container without a
specimen type has no clinical meaning (enforced as `NOT NULL`).

The optional `color` field records the tube cap color as a display hint for phlebotomists.

**Table:** `catalog_collection_containers`

---

## API Endpoints

All endpoints require a Bearer JWT. Unauthenticated requests return 401. Cross-tenant access
is blocked at the Hibernate filter layer (returns 404 or 422 as appropriate).

| Method | Path | Min Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/catalog/analytes` | `LAB_DOCTOR` | List analytes (paginated) |
| `GET` | `/api/v1/catalog/analytes/{id}` | `LAB_DOCTOR` | Get analyte by id |
| `POST` | `/api/v1/catalog/analytes` | `LAB_ADMIN` | Create analyte |
| `PUT` | `/api/v1/catalog/analytes/{id}` | `LAB_ADMIN` | Replace analyte (full update) |
| `GET` | `/api/v1/catalog/techniques` | `LAB_DOCTOR` | List techniques (paginated) |
| `GET` | `/api/v1/catalog/techniques/{id}` | `LAB_DOCTOR` | Get technique by id |
| `POST` | `/api/v1/catalog/techniques` | `LAB_ADMIN` | Create technique |
| `PUT` | `/api/v1/catalog/techniques/{id}` | `LAB_ADMIN` | Replace technique |
| `GET` | `/api/v1/catalog/specimen-types` | `LAB_DOCTOR` | List specimen types (paginated) |
| `GET` | `/api/v1/catalog/specimen-types/{id}` | `LAB_DOCTOR` | Get specimen type by id |
| `POST` | `/api/v1/catalog/specimen-types` | `LAB_ADMIN` | Create specimen type |
| `PUT` | `/api/v1/catalog/specimen-types/{id}` | `LAB_ADMIN` | Replace specimen type |
| `GET` | `/api/v1/catalog/collection-containers` | `LAB_DOCTOR` | List containers (paginated) |
| `GET` | `/api/v1/catalog/collection-containers/{id}` | `LAB_DOCTOR` | Get container by id |
| `POST` | `/api/v1/catalog/collection-containers` | `LAB_ADMIN` | Create container |
| `PUT` | `/api/v1/catalog/collection-containers/{id}` | `LAB_ADMIN` | Replace container |

> "Min Role" means the least-privileged role that can call the endpoint.
> `LAB_ADMIN`, `LAB_ANALYST`, `LAB_RECEPTIONIST`, and `LAB_DOCTOR` can all read catalog data.
> Only `LAB_ADMIN` can create or update catalog entries.

---

## Tenant Isolation

All catalog tables carry `tenant_id UUID NOT NULL`. The Hibernate `tenantFilter` is applied
automatically to all repository queries via `TenantFilterAspect`. Attempts to read or reference
another tenant's catalog entries return 404 (GET) or 422 (FK references).

---

## Validation Rules

| Entity | Rule |
|---|---|
| All | `code` and `name` are required; `code` must be unique within the tenant |
| Analyte | `resultType` is required; must be `NUMERIC`, `TEXT`, or `QUALITATIVE` |
| CollectionContainer | `specimenTypeId` is required and must belong to the caller's tenant |

Duplicate code in the same tenant returns `409 Conflict`.
Invalid `specimenTypeId` (missing or cross-tenant) returns `422 Unprocessable Entity`.
