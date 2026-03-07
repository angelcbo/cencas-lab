/**
 * Declares the Hibernate tenant filter once at the package level.
 * Hibernate 6 (Spring Boot 3.x): type = UUID.class — NOT the old Hibernate 5 string "uuid".
 * This @FilterDef is inherited by all entities annotated with @Filter("tenantFilter").
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
package com.cenicast.lis.common.persistence;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.util.UUID;
