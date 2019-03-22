// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class RoleMembershipTest {

    @Test
    public void operator_membership() {
        RoleMembership roles = new RoleMembership(Map.of(Role.hostedOperator, Set.of(Context.unlimitedIn(SystemName.main))));

        // Operator actions
        assertFalse(roles.allow(Action.create, "/not/explicitly/defined"));
        assertTrue(roles.allow(Action.create, "/controller/v1/foo"));
        assertTrue(roles.allow(Action.update, "/os/v1/bar"));
        assertTrue(roles.allow(Action.update, "/application/v4/tenant/t1/application/a1"));
        assertTrue(roles.allow(Action.update, "/application/v4/tenant/t2/application/a2"));
    }

    @Test
    public void tenant_membership() {
        RoleMembership roles = new RoleMembership(Map.of(Role.tenantAdmin,
                                                         Set.of(Context.limitedTo(TenantName.from("t1"),
                                                                                  ApplicationName.from("a1"),
                                                                                  SystemName.main))));

        assertFalse(roles.allow(Action.create, "/not/explicitly/defined"));
        assertFalse("Deny access to operator API", roles.allow(Action.create, "/controller/v1/foo"));
        assertFalse("Deny access to other tenant and app", roles.allow(Action.update, "/application/v4/tenant/t2/application/a2"));
        assertFalse("Deny access to other app", roles.allow(Action.update, "/application/v4/tenant/t1/application/a2"));
        assertTrue(roles.allow(Action.update, "/application/v4/tenant/t1/application/a1"));
        assertTrue("Global read access", roles.allow(Action.read, "/controller/v1/foo"));

        RoleMembership multiContext = new RoleMembership(Map.of(Role.tenantAdmin,
                                                         Set.of(Context.limitedTo(TenantName.from("t1"),
                                                                                  ApplicationName.from("a1"),
                                                                                  SystemName.main),
                                                                Context.limitedTo(TenantName.from("t2"),
                                                                                  ApplicationName.from("a2"),
                                                                                  SystemName.main))));
        assertFalse("Deny access to other tenant and app", multiContext.allow(Action.update, "/application/v4/tenant/t3/application/a3"));
        assertTrue(multiContext.allow(Action.update, "/application/v4/tenant/t2/application/a2"));
        assertTrue(multiContext.allow(Action.update, "/application/v4/tenant/t1/application/a1"));
        assertTrue("Global read access", roles.allow(Action.read, "/controller/v1/foo"));

        RoleMembership publicSystem = new RoleMembership(Map.of(Role.tenantAdmin,
                                                                Set.of(Context.limitedTo(TenantName.from("t1"),
                                                                                         ApplicationName.from("a1"),
                                                                                         SystemName.vaas))));
        assertFalse(publicSystem.allow(Action.read, "/controller/v1/foo"));
        assertTrue(multiContext.allow(Action.update, "/application/v4/tenant/t1/application/a1"));
    }

    @Test
    public void build_service_membership() {
        RoleMembership roles = new RoleMembership(Map.of(Role.tenantPipelineOperator, Set.of(Context.unlimitedIn(SystemName.main))));
        assertFalse(roles.allow(Action.create, "/not/explicitly/defined"));
        assertFalse(roles.allow(Action.update, "/application/v4/tenant/t1/application/a1"));
        assertTrue(roles.allow(Action.create, "/application/v4/tenant/t1/application/a1/jobreport"));
        assertFalse("No global read access", roles.allow(Action.read, "/controller/v1/foo"));
    }

    @Test
    public void multi_role_membership() {
        RoleMembership roles = new RoleMembership(Map.of(Role.tenantAdmin, Set.of(Context.limitedTo(TenantName.from("t1"),
                                                                                                    ApplicationName.from("a1"),
                                                                                                    SystemName.main)),
                                                             Role.tenantPipelineOperator, Set.of(Context.unlimitedIn(SystemName.main))));
        assertFalse(roles.allow(Action.create, "/not/explicitly/defined"));
        assertFalse(roles.allow(Action.create,"/controller/v1/foo"));
        assertTrue(roles.allow(Action.create, "/application/v4/tenant/t1/application/a1/jobreport"));
        assertTrue(roles.allow(Action.update, "/application/v4/tenant/t1/application/a1"));
        assertTrue("Global read access", roles.allow(Action.read, "/controller/v1/foo"));
    }

}
