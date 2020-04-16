// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import org.junit.Test;

import java.net.URI;
import java.util.List;

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
        assertTrue(mainEnforcer.allows(role, Action.read, URI.create("/routing/v1/")));
        assertTrue(mainEnforcer.allows(role, Action.read, URI.create("/routing/v1/status/environment/")));
        assertTrue(mainEnforcer.allows(role, Action.read, URI.create("/routing/v1/status/environment/prod")));
        assertTrue(mainEnforcer.allows(role, Action.create, URI.create("/routing/v1/inactive/environment/prod/region/us-north-1")));
    }

    @Test
    public void supporter_membership() {
        Role role = Role.hostedSupporter();

        // No create update or delete
        assertFalse(mainEnforcer.allows(role, Action.create, URI.create("/not/explicitly/defined")));
        assertFalse(mainEnforcer.allows(role, Action.create, URI.create("/controller/v1/foo")));
        assertFalse(mainEnforcer.allows(role, Action.update, URI.create("/os/v1/bar")));
        assertFalse(mainEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
        assertFalse(mainEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t2/application/a2")));
        assertFalse(mainEnforcer.allows(role, Action.delete, URI.create("/application/v4/tenant/t8/application/a6/instance/i1/environment/dev/region/r1")));

        // But reads is ok (but still only for valid paths)
        assertFalse(mainEnforcer.allows(role, Action.read, URI.create("/not/explicitly/defined")));
        assertTrue(mainEnforcer.allows(role, Action.read, URI.create("/controller/v1/foo")));
        assertTrue(mainEnforcer.allows(role, Action.read, URI.create("/os/v1/bar")));
        assertTrue(mainEnforcer.allows(role, Action.read, URI.create("/application/v4/tenant/t1/application/a1")));
        assertTrue(mainEnforcer.allows(role, Action.read, URI.create("/application/v4/tenant/t2/application/a2")));
        assertFalse(mainEnforcer.allows(role, Action.delete, URI.create("/application/v4/tenant/t8/application/a6/instance/i1/environment/dev/region/r1")));

        // Check that we are allowed to create tenants in public
        assertTrue(publicEnforcer.allows(role, Action.create, URI.create("/application/v4/tenant/t1")));
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
        Role role = Role.buildService(TenantName.from("t1"), ApplicationName.from("a1"));
        assertFalse(publicEnforcer.allows(role, Action.create, URI.create("/not/explicitly/defined")));
        assertFalse(publicEnforcer.allows(role, Action.update, URI.create("/application/v4/tenant/t1/application/a1")));
        assertTrue(publicEnforcer.allows(role, Action.create, URI.create("/application/v4/tenant/t1/application/a1/submit")));
        assertFalse("No global read access", publicEnforcer.allows(role, Action.read, URI.create("/controller/v1/foo")));
    }

    @Test
    public void new_implications() {
        TenantName tenant1 = TenantName.from("t1");
        ApplicationName application1 = ApplicationName.from("a1");
        ApplicationName application2 = ApplicationName.from("a2");

        Role tenantAdmin1 = Role.administrator(tenant1);
        Role tenantDeveloper1 = Role.developer(tenant1);
        Role applicationHeadless11 = Role.headless(tenant1, application1);
        Role applicationHeadless12 = Role.headless(tenant1, application2);

        assertFalse(tenantAdmin1.implies(tenantDeveloper1));
        assertFalse(tenantAdmin1.implies(applicationHeadless11));
        assertFalse(applicationHeadless11.implies(applicationHeadless12));
    }

    @Test
    public void system_flags() {
        URI deployUri = URI.create("/system-flags/v1/deploy");
        Action action = Action.update;
        assertTrue(mainEnforcer.allows(Role.systemFlagsDeployer(), action, deployUri));
        assertTrue(mainEnforcer.allows(Role.hostedOperator(), action, deployUri));
        assertFalse(mainEnforcer.allows(Role.hostedSupporter(), action, deployUri));
        assertFalse(mainEnforcer.allows(Role.systemFlagsDryrunner(), action, deployUri));
        assertFalse(mainEnforcer.allows(Role.everyone(), action, deployUri));

        URI dryrunUri = URI.create("/system-flags/v1/dryrun");
        assertTrue(mainEnforcer.allows(Role.systemFlagsDeployer(), action, dryrunUri));
        assertTrue(mainEnforcer.allows(Role.hostedOperator(), action, dryrunUri));
        assertFalse(mainEnforcer.allows(Role.hostedSupporter(), action, dryrunUri));
        assertTrue(mainEnforcer.allows(Role.systemFlagsDryrunner(), action, dryrunUri));
        assertFalse(mainEnforcer.allows(Role.everyone(), action, dryrunUri));
    }

    @Test
    public void routing() {
        var tenantUrl = URI.create("/routing/v1/status/tenant/t1");
        var applicationUrl = URI.create("/routing/v1/status/tenant/t1/application/a1");
        var instanceUrl = URI.create("/routing/v1/status/tenant/t1/application/a1/instance/i1");
        var deploymentUrl = URI.create("/routing/v1/status/tenant/t1/application/a1/instance/i1/environment/prod/region/us-north-1");
        // Read
        for (var url : List.of(tenantUrl, applicationUrl, instanceUrl, deploymentUrl)) {
            var allowedRole = Role.reader(TenantName.from("t1"));
            var disallowedRole = Role.reader(TenantName.from("t2"));
            assertTrue(allowedRole + " can read " + url, mainEnforcer.allows(allowedRole, Action.read, url));
            assertFalse(disallowedRole + " cannot read " + url, mainEnforcer.allows(disallowedRole, Action.read, url));
        }

        // Write
        {
            var url = URI.create("/routing/v1/inactive/tenant/t1/application/a1/instance/i1/environment/prod/region/us-north-1");
            var allowedRole = Role.developer(TenantName.from("t1"));
            var disallowedRole = Role.developer(TenantName.from("t2"));
            assertTrue(allowedRole + " can override status at " + url, mainEnforcer.allows(allowedRole, Action.create, url));
            assertTrue(allowedRole + " can clear status at " + url, mainEnforcer.allows(allowedRole, Action.delete, url));
            assertFalse(disallowedRole + " cannot override status at " + url, mainEnforcer.allows(disallowedRole, Action.create, url));
            assertFalse(disallowedRole + " cannot clear status at " + url, mainEnforcer.allows(disallowedRole, Action.delete, url));
        }
    }

}
