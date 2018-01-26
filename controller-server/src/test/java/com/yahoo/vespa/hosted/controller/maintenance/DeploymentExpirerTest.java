// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class DeploymentExpirerTest {

    private DeploymentTester tester;

    @Before
    public void before() {
        tester = new DeploymentTester();
    }

    @Test
    public void testDeploymentExpiry() {
        tester.controllerTester().zoneRegistry().setDeploymentTimeToLive(
                ZoneId.from(Environment.dev, RegionName.from("us-east-1")),
                Duration.ofDays(14)
        );
        DeploymentExpirer expirer = new DeploymentExpirer(tester.controller(), Duration.ofDays(10),
                                                          tester.clock(), new JobControl(new MockCuratorDb()));
        Application devApp = tester.createApplication("app1", "tenant1", 123L, 1L);
        Application prodApp = tester.createApplication("app2", "tenant2", 456L, 2L);

        // Deploy dev
        tester.controllerTester().deploy(devApp, tester.controllerTester().toZone(Environment.dev));

        // Deploy prod
        ApplicationPackage prodAppPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();
        tester.deployCompletely(prodApp, prodAppPackage);

        assertEquals(1, permanentDeployments(devApp).size());
        assertEquals(1, permanentDeployments(prodApp).size());

        // Not expired at first
        expirer.maintain();
        assertEquals(1, permanentDeployments(devApp).size());
        assertEquals(1, permanentDeployments(prodApp).size());

        // The dev application is removed
        tester.clock().advance(Duration.ofDays(15));
        expirer.maintain();
        assertEquals(0, permanentDeployments(devApp).size());
        assertEquals(1, permanentDeployments(prodApp).size());
    }

    private List<Deployment> permanentDeployments(Application application) {
        return tester.controller().applications().get(application.id()).get().deployments().values().stream()
                .filter(deployment -> deployment.zone().environment() != Environment.test &&
                                      deployment.zone().environment() != Environment.staging)
                .collect(Collectors.toList());
    }

}
