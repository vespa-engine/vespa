// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import org.junit.Test;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class RoleTest {

    private static final Enforcer mainEnforcer = new Enforcer(SystemName.main);
    private static final Enforcer publicEnforcer = new Enforcer(SystemName.Public);
    private static final Enforcer publicCdEnforcer = new Enforcer(SystemName.PublicCd);

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

        // Check that we are allowed to create tenants in public.
        // hostedSupporter isn't actually allowed to create tenants - but any logged in user will be a member of the "everyone" role.
        assertTrue(publicEnforcer.allows(Role.everyone(), Action.create, URI.create("/application/v4/tenant/t1")));
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

    @Test
    public void payment_instrument() {
        URI paymentInstrumentUri = URI.create("/billing/v1/tenant/t1/instrument/foobar");
        URI tenantPaymentInstrumentUri = URI.create("/billing/v1/tenant/t1/instrument");
        URI tokenUri = URI.create("/billing/v1/tenant/t1/token");

        Role user = Role.reader(TenantName.from("t1"));
        assertTrue(publicCdEnforcer.allows(user, Action.read, paymentInstrumentUri));
        assertTrue(publicCdEnforcer.allows(user, Action.delete, paymentInstrumentUri));
        assertFalse(publicCdEnforcer.allows(user, Action.update, tenantPaymentInstrumentUri));
        assertFalse(publicCdEnforcer.allows(user, Action.read, tokenUri));

        Role developer = Role.developer(TenantName.from("t1"));
        assertTrue(publicCdEnforcer.allows(developer, Action.read, paymentInstrumentUri));
        assertTrue(publicCdEnforcer.allows(developer, Action.delete, paymentInstrumentUri));
        assertFalse(publicCdEnforcer.allows(developer, Action.update, tenantPaymentInstrumentUri));
        assertFalse(publicCdEnforcer.allows(developer, Action.read, tokenUri));

        Role admin = Role.administrator(TenantName.from("t1"));
        assertTrue(publicCdEnforcer.allows(admin, Action.read, paymentInstrumentUri));
        assertTrue(publicCdEnforcer.allows(admin, Action.delete, paymentInstrumentUri));
        assertTrue(publicCdEnforcer.allows(admin, Action.update, tenantPaymentInstrumentUri));
        assertTrue(publicCdEnforcer.allows(admin, Action.read, tokenUri));
    }

    @Test
    public void billing_tenant() {
        URI billing = URI.create("/billing/v1/tenant/t1/billing");

        Role user = Role.reader(TenantName.from("t1"));
        Role developer = Role.developer(TenantName.from("t1"));
        Role admin = Role.administrator(TenantName.from("t1"));

        Stream.of(user, developer, admin).forEach(role -> {
            assertTrue(publicCdEnforcer.allows(role, Action.read, billing));
            assertFalse(publicCdEnforcer.allows(role, Action.update, billing));
            assertFalse(publicCdEnforcer.allows(role, Action.delete, billing));
            assertFalse(publicCdEnforcer.allows(role, Action.create, billing));
        });

    }

    @Test
    public void billing_test() {
        var tester = new EnforcerTester(publicEnforcer);

        var accountant = Role.hostedAccountant();
        var operator = Role.hostedOperator();
        var reader = Role.reader(TenantName.from("t1"));
        var developer = Role.developer(TenantName.from("t1"));
        var admin = Role.administrator(TenantName.from("t1"));
        var otherAdmin = Role.administrator(TenantName.from("t2"));

        tester.on("/billing/v1/tenant/t1/token")
                .assertAction(accountant)
                .assertAction(operator)
                .assertAction(reader)
                .assertAction(developer)
                .assertAction(admin,    Action.read)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/tenant/t1/instrument")
                .assertAction(accountant)
                .assertAction(operator,                 Action.read)
                .assertAction(reader,                   Action.read,                Action.delete)
                .assertAction(developer,                Action.read,                Action.delete)
                .assertAction(admin,                    Action.read, Action.update, Action.delete)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/tenant/t1/instrument/i1")
                .assertAction(accountant)
                .assertAction(operator,  Action.read)
                .assertAction(reader,    Action.read,                Action.delete)
                .assertAction(developer, Action.read,                Action.delete)
                .assertAction(admin,     Action.read, Action.update, Action.delete)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/tenant/t1/billing")
                .assertAction(accountant)
                .assertAction(operator,  Action.read)
                .assertAction(reader,    Action.read)
                .assertAction(developer, Action.read)
                .assertAction(admin,     Action.read)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/tenant/t1/plan")
                .assertAction(accountant, Action.update)
                .assertAction(operator,   Action.read)
                .assertAction(reader)
                .assertAction(developer)
                .assertAction(admin,      Action.update)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/tenant/t1/collection")
                .assertAction(accountant, Action.update)
                .assertAction(operator,   Action.read)
                .assertAction(reader)
                .assertAction(developer)
                .assertAction(admin)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/billing")
                .assertAction(accountant, Action.create, Action.read, Action.update, Action.delete)
                .assertAction(operator,   Action.read)
                .assertAction(reader)
                .assertAction(developer)
                .assertAction(admin)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/invoice/tenant/t1/line-item")
                .assertAction(accountant, Action.create, Action.read, Action.update, Action.delete)
                .assertAction(operator,                  Action.read)
                .assertAction(reader)
                .assertAction(developer)
                .assertAction(admin)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/invoice")
                .assertAction(accountant, Action.create, Action.read, Action.update, Action.delete)
                .assertAction(operator,                  Action.read)
                .assertAction(reader)
                .assertAction(developer)
                .assertAction(admin)
                .assertAction(otherAdmin);

        tester.on("/billing/v1/invoice/i1/status")
                .assertAction(accountant, Action.create, Action.read, Action.update, Action.delete)
                .assertAction(operator,                  Action.read)
                .assertAction(reader)
                .assertAction(developer)
                .assertAction(admin)
                .assertAction(otherAdmin);
    }

    private static class EnforcerTester {
        private final Enforcer enforcer;
        private final URI resource;

        EnforcerTester(Enforcer enforcer) {
            this(enforcer, null);
        }

        EnforcerTester(Enforcer enforcer, URI uri) {
            this.enforcer = enforcer;
            this.resource = uri;
        }

        public EnforcerTester on(String uri) {
            return new EnforcerTester(enforcer, URI.create(uri));
        }

        public EnforcerTester assertAction(Role role, Action ...actions) {
            var allowed = List.of(actions);

            allowed.forEach(action -> {
                var msg = String.format("%s should be allowed to %s on %s", role, action, resource);
                assertTrue(msg, enforcer.allows(role, action, resource));
            });

            Action.all().stream().filter(a -> ! allowed.contains(a)).forEach(action -> {
                var msg = String.format("%s should not be allowed to %s on %s", role, action, resource);
                assertFalse(msg, enforcer.allows(role, action, resource));
            });

            return this;
        }
    }
}
