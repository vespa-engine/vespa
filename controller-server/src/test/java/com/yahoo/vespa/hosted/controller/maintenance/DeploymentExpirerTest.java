// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author bratseth
 */
public class DeploymentExpirerTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void testDeploymentExpiry() {
        ZoneId devZone = ZoneId.from(Environment.dev, RegionName.from("us-east-1"));
        tester.controllerTester().zoneRegistry().setDeploymentTimeToLive(devZone, Duration.ofDays(14));
        DeploymentExpirer expirer = new DeploymentExpirer(tester.controller(), Duration.ofDays(1));
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

        // Not expired at first
        expirer.maintain();
        assertEquals(1, permanentDeployments(devApp.instance()));
        assertEquals(1, permanentDeployments(prodApp.instance()));

        // Deploy dev unsuccessfully a few days before expiry
        tester.clock().advance(Duration.ofDays(12));
        tester.configServer().throwOnNextPrepare(new RuntimeException(getClass().getSimpleName()));
        tester.jobs().deploy(devApp.instanceId(), JobType.devUsEast1, Optional.empty(), appPackage);
        Run lastRun = tester.jobs().last(devApp.instanceId(), JobType.devUsEast1).get();
        assertSame(RunStatus.error, lastRun.status());
        Deployment deployment = tester.applications().requireInstance(devApp.instanceId())
                                      .deployments().get(devZone);
        assertEquals("Time of last run is after time of deployment", Duration.ofDays(12),
                     Duration.between(deployment.at(), lastRun.end().get()));

        // Dev application does not expire based on time of successful deployment
        tester.clock().advance(Duration.ofDays(2));
        expirer.maintain();
        assertEquals(1, permanentDeployments(devApp.instance()));
        assertEquals(1, permanentDeployments(prodApp.instance()));

        // Dev application expires when enough time has passed since most recent attempt
        // Redeployments done by DeploymentUpgrader do not affect this
        tester.clock().advance(Duration.ofDays(12).plus(Duration.ofSeconds(1)));
        tester.jobs().start(devApp.instanceId(), JobType.devUsEast1, lastRun.versions(), true);
        expirer.maintain();
        assertEquals(0, permanentDeployments(devApp.instance()));
        assertEquals(1, permanentDeployments(prodApp.instance()));
    }

    private long permanentDeployments(Instance instance) {
        return tester.controller().applications().requireInstance(instance.id()).deployments().values().stream()
                     .filter(deployment -> !deployment.zone().environment().isTest())
                     .count();
    }

}
