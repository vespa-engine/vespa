// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockRoleService;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mortent
 */
public class TenantRoleMaintainerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    void maintains_iam_roles_for_tenants_in_production() {
        var devAppTenant1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var prodAppTenant2 = tester.newDeploymentContext("tenant2", "app2", "default");
        var devAppTenant2 = tester.newDeploymentContext("tenant2", "app3", "default");
        var perfAppTenant1 = tester.newDeploymentContext("tenant3", "app1", "default");
        ApplicationPackage appPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        // Deploy dev apps
        devAppTenant1.runJob(DeploymentContext.devUsEast1, appPackage);
        devAppTenant2.runJob(DeploymentContext.devUsEast1, appPackage);

        // Deploy perf apps
        perfAppTenant1.runJob(DeploymentContext.perfUsEast3, appPackage);

        // Deploy prod
        prodAppTenant2.submit(appPackage).deploy();
        assertEquals(1, permanentDeployments(devAppTenant1.instance()));
        assertEquals(1, permanentDeployments(devAppTenant2.instance()));
        assertEquals(1, permanentDeployments(prodAppTenant2.instance()));

        var maintainer = new TenantRoleMaintainer(tester.controller(), Duration.ofDays(1));
        maintainer.maintain();

        var roleService = tester.controller().serviceRegistry().roleService();
        List<TenantName> tenantNames = ((MockRoleService) roleService).maintainedTenants();

        assertTrue(tenantNames.containsAll(List.of(prodAppTenant2.application().id().tenant(), perfAppTenant1.application().id().tenant())));
    }

    private long permanentDeployments(Instance instance) {
        return tester.controller().applications().requireInstance(instance.id()).deployments().values().stream()
                .filter(deployment -> !deployment.zone().environment().isTest())
                .count();
    }

}
