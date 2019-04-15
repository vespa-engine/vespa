// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class RoleTest {

    private static final Enforcer mainEnforcer = new Enforcer(SystemName.main);
    private static final Enforcer vaasEnforcer = new Enforcer(SystemName.vaas);

    @Test
    public void operator_membership() {
        Role role = new Roles(SystemName.main).hostedOperator();

        // Operator actions
        assertFalse(role.allows(Action.create, URI.create("/not/explicitly/defined"), mainEnforcer));
        assertTrue(role.allows(Action.create, URI.create("/controller/v1/foo"), mainEnforcer));
        assertTrue(role.allows(Action.update, URI.create("/os/v1/bar"), mainEnforcer));
        assertTrue(role.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1"), mainEnforcer));
        assertTrue(role.allows(Action.update, URI.create("/application/v4/tenant/t2/application/a2"), mainEnforcer));
    }

    @Test
    public void tenant_membership() {
        Role role = new Roles(SystemName.main).athenzTenantAdmin(TenantName.from("t1"));
        assertFalse(role.allows(Action.create, URI.create("/not/explicitly/defined"), mainEnforcer));
        assertFalse("Deny access to operator API", role.allows(Action.create, URI.create("/controller/v1/foo"), mainEnforcer));
        assertFalse("Deny access to other tenant and app", role.allows(Action.update, URI.create("/application/v4/tenant/t2/application/a2"), mainEnforcer));
        assertTrue(role.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1"), mainEnforcer));

        Role publicSystem = new Roles(SystemName.vaas).athenzTenantAdmin(TenantName.from("t1"));
        assertFalse(publicSystem.allows(Action.read, URI.create("/controller/v1/foo"), vaasEnforcer));
        assertTrue(publicSystem.allows(Action.read, URI.create("/badge/v1/badge"), vaasEnforcer));
        assertTrue(publicSystem.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1"), vaasEnforcer));
    }

    @Test
    public void build_service_membership() {
        Role role = new Roles(SystemName.vaas).tenantPipeline(TenantName.from("t1"), ApplicationName.from("a1"));
        assertFalse(role.allows(Action.create, URI.create("/not/explicitly/defined"), vaasEnforcer));
        assertFalse(role.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1"), vaasEnforcer));
        assertTrue(role.allows(Action.create, URI.create("/application/v4/tenant/t1/application/a1/jobreport"), vaasEnforcer));
        assertFalse("No global read access", role.allows(Action.read, URI.create("/controller/v1/foo"), vaasEnforcer));
    }

    @Test
    public void implications() {
        Roles roles = new Roles(SystemName.main);
        TenantName tenant1 = TenantName.from("t1");
        ApplicationName application1 = ApplicationName.from("a1");
        TenantName tenant2 = TenantName.from("t2");
        ApplicationName application2 = ApplicationName.from("a2");

        Role tenantOwner1 = roles.tenantOwner(tenant1);
        Role tenantAdmin1 = roles.tenantAdmin(tenant1);
        Role tenantAdmin2 = roles.tenantAdmin(tenant2);
        Role tenantOperator1 = roles.tenantOperator(tenant1);
        Role applicationAdmin11 = roles.applicationAdmin(tenant1, application1);
        Role applicationOperator11 = roles.applicationOperator(tenant1, application1);
        Role applicationDeveloper11 = roles.applicationDeveloper(tenant1, application1);
        Role applicationReader11 = roles.applicationReader(tenant1, application1);
        Role applicationReader12 = roles.applicationReader(tenant1, application2);
        Role applicationReader22 = roles.applicationReader(tenant2, application2);

        assertFalse(tenantOwner1.implies(tenantOwner1));
        assertTrue(tenantOwner1.implies(tenantAdmin1));
        assertFalse(tenantOwner1.implies(tenantAdmin2));
        assertTrue(tenantOwner1.implies(tenantOperator1));
        assertTrue(tenantOwner1.implies(applicationAdmin11));
        assertTrue(tenantOwner1.implies(applicationReader11));
        assertTrue(tenantOwner1.implies(applicationReader12));
        assertFalse(tenantOwner1.implies(applicationReader22));

        assertFalse(tenantAdmin1.implies(tenantOwner1));
        assertFalse(tenantAdmin1.implies(tenantAdmin2));
        assertTrue(tenantAdmin1.implies(applicationDeveloper11));

        assertFalse(tenantOperator1.implies(applicationReader11));

        assertFalse(applicationAdmin11.implies(tenantAdmin1));
        assertFalse(applicationAdmin11.implies(tenantOperator1));
        assertTrue(applicationAdmin11.implies(applicationOperator11));
        assertTrue(applicationAdmin11.implies(applicationDeveloper11));
        assertTrue(applicationAdmin11.implies(applicationReader11));
        assertFalse(applicationAdmin11.implies(applicationReader12));
        assertFalse(applicationAdmin11.implies(applicationReader22));

        assertFalse(applicationOperator11.implies(applicationDeveloper11));
        assertTrue(applicationOperator11.implies(applicationReader11));

        assertFalse(applicationDeveloper11.implies(applicationOperator11));
        assertTrue(applicationDeveloper11.implies(applicationReader11));
    }

}
