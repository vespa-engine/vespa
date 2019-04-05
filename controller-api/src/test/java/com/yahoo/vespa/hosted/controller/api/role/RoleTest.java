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

    @Test
    public void operator_membership() {
        Role role = new Roles(SystemName.main).hostedOperator();

        // Operator actions
        assertFalse(role.allows(Action.create, URI.create("/not/explicitly/defined")));
        assertTrue(role.allows(Action.create, URI.create("/controller/v1/foo")));
        assertTrue(role.allows(Action.update, URI.create("/os/v1/bar")));
        assertTrue(role.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
        assertTrue(role.allows(Action.update, URI.create("/application/v4/tenant/t2/application/a2")));
    }

    @Test
    public void tenant_membership() {
        Role role = new Roles(SystemName.main).athenzTenantAdmin(TenantName.from("t1"));
        assertFalse(role.allows(Action.create, URI.create("/not/explicitly/defined")));
        assertFalse("Deny access to operator API", role.allows(Action.create, URI.create("/controller/v1/foo")));
        assertFalse("Deny access to other tenant and app", role.allows(Action.update, URI.create("/application/v4/tenant/t2/application/a2")));
        assertTrue(role.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1")));

        Role publicSystem = new Roles(SystemName.vaas).athenzTenantAdmin(TenantName.from("t1"));
        assertFalse(publicSystem.allows(Action.read, URI.create("/controller/v1/foo")));
        assertTrue(publicSystem.allows(Action.read, URI.create("/badge/v1/badge")));
        assertTrue(publicSystem.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
    }

    @Test
    public void build_service_membership() {
        Role role = new Roles(SystemName.vaas).tenantPipeline(TenantName.from("t1"), ApplicationName.from("a1"));
        assertFalse(role.allows(Action.create, URI.create("/not/explicitly/defined")));
        assertFalse(role.allows(Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
        assertTrue(role.allows(Action.create, URI.create("/application/v4/tenant/t1/application/a1/jobreport")));
        assertFalse("No global read access", role.allows(Action.read, URI.create("/controller/v1/foo")));
    }

}
