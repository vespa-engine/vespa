// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class DeploymentExpirerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void testDeploymentExpiry() {
        tester.controllerTester().zoneRegistry().setDeploymentTimeToLive(
                ZoneId.from(Environment.dev, RegionName.from("us-east-1")),
                Duration.ofDays(14)
        );
        DeploymentExpirer expirer = new DeploymentExpirer(tester.controller(), Duration.ofDays(10),
                                                          new JobControl(new MockCuratorDb()));
        Application devApp = tester.createApplication("tenant1", "app1", "default");
        Application prodApp = tester.createApplication("tenant2", "app2", "default");

        ApplicationPackage appPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        Instance devInstance = tester.instance(devApp.id().defaultInstance());
        Instance prodInstance = tester.instance(prodApp.id().defaultInstance());

        // Deploy dev
        tester.runJob(devInstance.id(), JobType.devUsEast1, appPackage);

        // Deploy prod
        tester.deployNewSubmission(prodApp.id(), tester.newSubmission(prodApp.id(), appPackage));

        assertEquals(1, permanentDeployments(devInstance).size());
        assertEquals(1, permanentDeployments(prodInstance).size());

        // Not expired at first
        expirer.maintain();
        assertEquals(1, permanentDeployments(devInstance).size());
        assertEquals(1, permanentDeployments(prodInstance).size());

        // The dev application is removed
        tester.clock().advance(Duration.ofDays(15));
        expirer.maintain();
        assertEquals(0, permanentDeployments(devInstance).size());
        assertEquals(1, permanentDeployments(prodInstance).size());
    }

    private List<Deployment> permanentDeployments(Instance instance) {
        return tester.controller().applications().getInstance(instance.id()).get().deployments().values().stream()
                     .filter(deployment -> deployment.zone().environment() != Environment.test &&
                                      deployment.zone().environment() != Environment.staging)
                     .collect(Collectors.toList());
    }

}
