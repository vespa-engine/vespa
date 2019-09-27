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
    private static final Enforcer publicEnforcer = new Enforcer(SystemName.Public);

    @Test
    public void operator_membership() {
        Role role = Role.hostedOperator();

        // Operator actions
        assertFalse(mainEnforcer.allows(role, Action.create, URI.create("/not/explicitly/defined")));
        assertTrue(mainEnforcer.allows(role, Action.create, URI.create("/controller/v1/foo")));
        assertTrue(mainEnforcer.allows(role, Action.update, URI.create("/os/v1/bar")));
        assertTrue(mainEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
        assertTrue(mainEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t2/application/a2")));
    }

    @Test
    public void tenant_membership() {
        Role role = Role.athenzTenantAdmin(TenantName.from("t1"));
        assertFalse(mainEnforcer.allows(role, Action.create, URI.create("/not/explicitly/defined")));
        assertFalse("Deny access to operator API", mainEnforcer.allows(role, Action.create, URI.create("/controller/v1/foo")));
        assertFalse("Deny access to other tenant and app", mainEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t2/application/a2")));
        assertTrue(mainEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t1/application/a1")));

        Role publicSystem = Role.athenzTenantAdmin(TenantName.from("t1"));
        assertFalse(publicEnforcer.allows(publicSystem, Action.read, URI.create("/controller/v1/foo")));
        assertTrue(publicEnforcer.allows(publicSystem, Action.read, URI.create("/badge/v1/badge")));
        assertTrue(publicEnforcer.allows(publicSystem, Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
    }

    @Test
    public void build_service_membership() {
        Role role = Role.tenantPipeline(TenantName.from("t1"), ApplicationName.from("a1"));
        assertFalse(publicEnforcer.allows(role, Action.create, URI.create("/not/explicitly/defined")));
        assertFalse(publicEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
        assertTrue(publicEnforcer.allows(role, Action.create, URI.create("/application/v4/tenant/t1/application/a1/jobreport")));
        assertFalse("No global read access", publicEnforcer.allows(role, Action.read, URI.create("/controller/v1/foo")));
    }

    @Test
    public void implications() {
        TenantName tenant1 = TenantName.from("t1");
        ApplicationName application1 = ApplicationName.from("a1");
        TenantName tenant2 = TenantName.from("t2");
        ApplicationName application2 = ApplicationName.from("a2");

        Role tenantAdmin1 = Role.administrator(tenant1);
        Role tenantAdmin2 = Role.administrator(tenant2);
        Role tenantDeveloper1 = Role.developer(tenant1);
        Role applicationHeadless11 = Role.headless(tenant1, application1);
        Role applicationHeadless12 = Role.headless(tenant1, application2);

        assertFalse(tenantAdmin1.implies(tenantAdmin2));
        assertFalse(tenantAdmin1.implies(tenantDeveloper1));
        assertFalse(tenantAdmin1.implies(applicationHeadless11));
        assertFalse(applicationHeadless11.implies(applicationHeadless12));
    }

}
