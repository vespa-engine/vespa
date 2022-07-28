// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author olaa
 */
public class UserManagementMaintainerTest {

    private final String TENANT_1 = "tenant1";
    private final String TENANT_2 = "tenant2";
    private final String APP_NAME = "some-app";

    @Test
    void deletes_tenant_when_not_public() {
        var tester = createTester(SystemName.main);
        var maintainer = new UserManagementMaintainer(tester.controller(), Duration.ofMinutes(5), tester.serviceRegistry().roleMaintainer());
        maintainer.maintain();

        var tenants = tester.controller().tenants().asList();
        var apps = tester.controller().applications().asList();
        assertEquals(1, tenants.size());
        assertEquals(1, apps.size());
        assertEquals(TENANT_2, tenants.get(0).name().value());
    }

    @Test
    void no_tenant_deletion_in_public() {
        var tester = createTester(SystemName.Public);
        var maintainer = new UserManagementMaintainer(tester.controller(), Duration.ofMinutes(5), tester.serviceRegistry().roleMaintainer());
        maintainer.maintain();

        var tenants = tester.controller().tenants().asList();
        var apps = tester.controller().applications().asList();
        assertEquals(2, tenants.size());
        assertEquals(2, apps.size());
    }

    private ControllerTester createTester(SystemName systemName) {
        var tester = new ControllerTester(systemName);
        tester.createTenant(TENANT_1);
        tester.createTenant(TENANT_2);
        tester.createApplication(TENANT_1, APP_NAME);
        tester.createApplication(TENANT_2, APP_NAME);

        var tenantToDelete = tester.controller().tenants().get(TENANT_1).get();
        tester.serviceRegistry().roleMaintainerMock().mockTenantToDelete(tenantToDelete);
        return tester;
    }

}