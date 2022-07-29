// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.devUsEast1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.maintenance.DeploymentUpgrader.mostLikelyWeeHour;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 */
public class DeploymentUpgraderTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    void testDeploymentUpgrading() {
        ZoneId devZone = ZoneId.from(Environment.dev, RegionName.from("us-east-1"));
        DeploymentUpgrader upgrader = new DeploymentUpgrader(tester.controller(), Duration.ofDays(1));
        var devApp = tester.newDeploymentContext("tenant1", "app1", "default");
        var prodApp = tester.newDeploymentContext("tenant2", "app2", "default");

        ApplicationPackage appPackage = new ApplicationPackageBuilder().region("us-west-1").build();
        Version systemVersion = tester.controller().readSystemVersion();
        Instant start = tester.clock().instant().truncatedTo(MILLIS);

        devApp.runJob(devUsEast1, appPackage);
        prodApp.submit(appPackage).deploy();
        assertEquals(systemVersion, tester.jobs().last(devApp.instanceId(), devUsEast1).get().versions().targetPlatform());
        assertEquals(systemVersion, tester.jobs().last(prodApp.instanceId(), productionUsWest1).get().versions().targetPlatform());

        // Not upgraded initially
        upgrader.maintain();
        assertEquals(start, tester.jobs().last(devApp.instanceId(), devUsEast1).get().start());
        assertEquals(start, tester.jobs().last(prodApp.instanceId(), productionUsWest1).get().start());

        // Not upgraded immediately after system upgrades
        tester.controllerTester().upgradeSystem(new Version(7, 8, 9));
        upgrader.maintain();
        assertEquals(start, tester.jobs().last(devApp.instanceId(), devUsEast1).get().start());
        assertEquals(start, tester.jobs().last(prodApp.instanceId(), productionUsWest1).get().start());

        // 11 hours pass, but not upgraded since it's not likely in the middle of the night
        tester.clock().advance(Duration.ofHours(11));
        upgrader.maintain();
        assertEquals(start, tester.jobs().last(devApp.instanceId(), devUsEast1).get().start());
        assertEquals(start, tester.jobs().last(prodApp.instanceId(), productionUsWest1).get().start());

        // 14 hours pass, and the dev deployment, only, is upgraded
        tester.clock().advance(Duration.ofHours(3));
        upgrader.maintain();
        assertEquals(tester.clock().instant().truncatedTo(MILLIS), tester.jobs().last(devApp.instanceId(), devUsEast1).get().start());
        assertTrue(tester.jobs().last(devApp.instanceId(), devUsEast1).get().isRedeployment());
        assertEquals(start, tester.jobs().last(prodApp.instanceId(), productionUsWest1).get().start());
        devApp.runJob(devUsEast1);

        // After the upgrade, the dev app is mostly (re)deployed to at night, but this doesn't affect what is likely the night.
        tester.controllerTester().upgradeSystem(new Version(7, 9, 11));
        tester.clock().advance(Duration.ofHours(48));
        upgrader.maintain();
        assertEquals(tester.clock().instant().truncatedTo(MILLIS), tester.jobs().last(devApp.instanceId(), devUsEast1).get().start());
    }

    @Test
    void testNight() {
        assertEquals(16, mostLikelyWeeHour(new int[]{0, 1, 2, 3, 4, 5, 6}));
        assertEquals(14, mostLikelyWeeHour(new int[]{22, 23, 0, 1, 2, 3, 4}));
        assertEquals(18, mostLikelyWeeHour(new int[]{6, 5, 4, 3, 2, 1, 0}));
        assertEquals(20, mostLikelyWeeHour(new int[]{0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 11}));
    }

}
