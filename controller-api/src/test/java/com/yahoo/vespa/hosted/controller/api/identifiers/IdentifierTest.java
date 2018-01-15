// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdentifierTest {

    @Test(expected = IllegalArgumentException.class)
    public void existing_tenant_id_not_empty() {
        new TenantId("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void existing_tenant_id_must_check_pattern() {
        new TenantId("`");
    }

    @Test(expected = IllegalArgumentException.class)
    public void default_not_allowed_for_tenants() {
        new TenantId("default");
    }

    @Test
    public void existing_tenant_id_must_accept_valid_id() {
        new TenantId("msbe");
    }

    @Test(expected = IllegalArgumentException.class)
    public void existing_tenant_id_cannot_be_uppercase() {
        new TenantId("MixedCaseTenant");
    }

    @Test(expected = IllegalArgumentException.class)
    public void existing_tenant_id_cannot_contain_dots() {
        new TenantId("tenant.with.dots");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_tenant_id_cannot_contain_underscore() {
        TenantId.validate("underscore_tenant");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_tenant_id_cannot_contain_dot() {
        TenantId.validate("tenant.with.dots");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_tenant_id_cannot_contain_uppercase() {
        TenantId.validate("UppercaseTenant");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_tenant_id_cannot_start_with_dash() {
        TenantId.validate("-tenant");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_tenant_id_cannot_end_with_dash() {
        TenantId.validate("tenant-");
    }

    @Test(expected = IllegalArgumentException.class)
    public void existing_application_id_cannot_be_uppercase() {
        new ApplicationId("MixedCaseApplication");
    }

    @Test(expected = IllegalArgumentException.class)
    public void existing_application_id_cannot_contain_dots() {
        new ApplicationId("application.with.dots");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_application_id_cannot_contain_underscore() {
        ApplicationId.validate("underscore_application");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_application_id_cannot_contain_dot() {
        ApplicationId.validate("application.with.dots");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_application_id_cannot_contain_uppercase() {
        ApplicationId.validate("UppercaseApplication");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_application_id_cannot_start_with_dash() {
        ApplicationId.validate("-application");
    }

    @Test(expected = IllegalArgumentException.class)
    public void new_application_id_cannot_end_with_dash() {
        ApplicationId.validate("application-");
    }

    @Test(expected = IllegalArgumentException.class)
    public void instance_id_cannot_be_uppercase() {
        new InstanceId("MixedCaseInstance");
    }

    @Test
    public void rotation_id_may_contain_dot() {
        new RotationId("rotation.id.with.dot");
    }

    @Test
    public void user_tenant_id_does_not_contain_underscore() {
        assertEquals("by-under-score-user", new UserId("under_score_user").toTenantId().id());
    }

    @Test
    public void athenz_parent_domain_is_without_name_suffix() {
        assertEquals(new AthenzDomain("home.john"), new AthenzDomain("home.john.myapp").getParent());
    }

    @Test
    public void athenz_domain_name_is_last_suffix() {
        assertEquals("myapp", new AthenzDomain("home.john.myapp").getNameSuffix());
    }

    @Test
    public void domain_without_dot_is_toplevel() {
        assertTrue(new AthenzDomain("toplevel").isTopLevelDomain());
        assertFalse(new AthenzDomain("not.toplevel").isTopLevelDomain());
    }

    @Test
    public void dns_names_has_no_underscore() {
        assertEquals("a-b-c", new ApplicationId("a_b_c").toDns());
    }

    @Test(expected = IllegalArgumentException.class)
    public void identifiers_cannot_be_named_api() {
        new ApplicationId("api");
    }


    @Test
    public void application_instance_id_dotted_string_is_subindentifers_concatinated_with_dots() {
        DeploymentId id = new DeploymentId(com.yahoo.config.provision.ApplicationId.from("tenant", "application", "instance"),
                                           ZoneId.from("prod", "region"));
        assertEquals("tenant.application.prod.region.instance", id.dottedString());
    }
}
