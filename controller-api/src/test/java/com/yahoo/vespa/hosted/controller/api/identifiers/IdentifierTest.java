// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.yahoo.config.provision.zone.ZoneId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author smorgrav
 */
public class IdentifierTest {

    @Test
    void existing_tenant_id_not_empty() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantId("");
        });
    }

    @Test
    void existing_tenant_id_must_check_pattern() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantId("`");
        });
    }

    @Test
    void default_not_allowed_for_tenants() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantId("default");
        });
    }

    @Test
    void existing_tenant_id_must_accept_valid_id() {
        new TenantId("msbe");
    }

    @Test
    void existing_tenant_id_cannot_be_uppercase() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantId("MixedCaseTenant");
        });
    }

    @Test
    void existing_tenant_id_cannot_contain_dots() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TenantId("tenant.with.dots");
        });
    }

    @Test
    void new_tenant_id_cannot_contain_underscore() {
        assertThrows(IllegalArgumentException.class, () -> {
            TenantId.validate("underscore_tenant");
        });
    }

    @Test
    void new_tenant_id_cannot_contain_dot() {
        assertThrows(IllegalArgumentException.class, () -> {
            TenantId.validate("tenant.with.dots");
        });
    }

    @Test
    void new_tenant_id_cannot_contain_uppercase() {
        assertThrows(IllegalArgumentException.class, () -> {
            TenantId.validate("UppercaseTenant");
        });
    }

    @Test
    void new_tenant_id_cannot_start_with_dash() {
        assertThrows(IllegalArgumentException.class, () -> {
            TenantId.validate("-tenant");
        });
    }

    @Test
    void new_tenant_id_cannot_end_with_dash() {
        assertThrows(IllegalArgumentException.class, () -> {
            TenantId.validate("tenant-");
        });
    }

    @Test
    void existing_application_id_cannot_be_uppercase() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ApplicationId("MixedCaseApplication");
        });
    }

    @Test
    void existing_application_id_cannot_contain_dots() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ApplicationId("application.with.dots");
        });
    }

    @Test
    void new_application_id_cannot_contain_underscore() {
        assertThrows(IllegalArgumentException.class, () -> {
            ApplicationId.validate("underscore_application");
        });
    }

    @Test
    void new_application_id_cannot_contain_dot() {
        assertThrows(IllegalArgumentException.class, () -> {
            ApplicationId.validate("application.with.dots");
        });
    }

    @Test
    void new_application_id_cannot_contain_uppercase() {
        assertThrows(IllegalArgumentException.class, () -> {
            ApplicationId.validate("UppercaseApplication");
        });
    }

    @Test
    void new_application_id_cannot_start_with_dash() {
        assertThrows(IllegalArgumentException.class, () -> {
            ApplicationId.validate("-application");
        });
    }

    @Test
    void new_application_id_cannot_end_with_dash() {
        assertThrows(IllegalArgumentException.class, () -> {
            ApplicationId.validate("application-");
        });
    }

    @Test
    void instance_id_cannot_be_uppercase() {
        assertThrows(IllegalArgumentException.class, () -> {
            new InstanceId("MixedCaseInstance");
        });
    }

    @Test
    void dns_names_has_no_underscore() {
        assertEquals("a-b-c", new ApplicationId("a_b_c").toDns());
    }

    @Test
    void identifiers_cannot_be_named_api() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ApplicationId("api");
        });
    }

    @Test
    void application_instance_id_dotted_string_is_subindentifers_concatinated_with_dots() {
        DeploymentId id = new DeploymentId(com.yahoo.config.provision.ApplicationId.from("tenant", "application", "instance"),
                ZoneId.from("prod", "region"));
        assertEquals("tenant.application.prod.region.instance", id.dottedString());
    }

    @Test
    void revision_id_can_contain_application_version_number() {
        new RevisionId("1.0.1078-24825d1f6");
    }
}
