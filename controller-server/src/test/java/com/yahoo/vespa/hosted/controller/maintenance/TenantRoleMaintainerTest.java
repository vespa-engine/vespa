// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockRoleService;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class TenantRoleMaintainerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void maintains_iam_roles_for_tenants_in_production() {
        var devApp = tester.newDeploymentContext("tenant1", "app1", "default");
        var prodApp = tester.newDeploymentContext("tenant2", "app2", "default");
        ApplicationPackage appPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        // Deploy dev
        devApp.runJob(JobType.devUsEast1, appPackage);

        // Deploy prod
        prodApp.submit(appPackage).deploy();
        assertEquals(1, permanentDeployments(devApp.instance()));
        assertEquals(1, permanentDeployments(prodApp.instance()));

        var maintainer = new TenantRoleMaintainer(tester.controller(), Duration.ofDays(1));
        maintainer.maintain();

        var roleService = tester.controller().serviceRegistry().roleService();
        List<TenantName> tenantNames = ((MockRoleService) roleService).maintainedTenants();

        assertEquals(1, tenantNames.size());
        assertEquals(prodApp.application().id().tenant(), tenantNames.get(0));
    }

    private long permanentDeployments(Instance instance) {
        return tester.controller().applications().requireInstance(instance.id()).deployments().values().stream()
                .filter(deployment -> !deployment.zone().environment().isTest())
                .count();
    }

}
