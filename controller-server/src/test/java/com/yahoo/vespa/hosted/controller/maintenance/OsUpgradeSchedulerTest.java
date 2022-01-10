// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.OsRelease;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class OsUpgradeSchedulerTest {

    @Test
    public void schedule_calendar_versioned_release() {
        ControllerTester tester = new ControllerTester();
        OsUpgradeScheduler scheduler = new OsUpgradeScheduler(tester.controller(), Duration.ofDays(1));
        Instant t0 = Instant.parse("2021-01-23T00:00:00.00Z"); // Outside trigger period
        tester.clock().setInstant(t0);

        CloudName cloud = CloudName.from("cloud");
        ZoneApi zone = zone("prod.us-west-1", cloud);
        tester.zoneRegistry().setZones(zone).reprovisionToUpgradeOsIn(zone);

        // Initial run does nothing as the cloud does not have a target
        scheduler.maintain();
        assertTrue("No target set", tester.controller().osVersionTarget(cloud).isEmpty());

        // Target is set
        Version version0 = Version.fromString("7.0.0.20210123190005");
        tester.controller().upgradeOsIn(cloud, version0, Duration.ofDays(1), false);

        // Target remains unchanged as it hasn't expired yet
        for (var interval : List.of(Duration.ZERO, Duration.ofDays(30))) {
            tester.clock().advance(interval);
            scheduler.maintain();
            assertEquals(version0, tester.controller().osVersionTarget(cloud).get().osVersion().version());
        }

        // Just over 45 days pass, and a new target replaces the expired one
        Version version1 = Version.fromString("7.0.0.20210302");
        tester.clock().advance(Duration.ofDays(15).plus(Duration.ofSeconds(1)));
        scheduler.maintain();
        assertEquals("Target is unchanged because we're outside trigger period", version0,
                     tester.controller().osVersionTarget(cloud).get().osVersion().version());
        tester.clock().advance(Duration.ofHours(7)); // Put us inside the trigger period
        scheduler.maintain();
        assertEquals("New target set", version1,
                     tester.controller().osVersionTarget(cloud).get().osVersion().version());

        // A few days pass and target remains unchanged
        tester.clock().advance(Duration.ofDays(2));
        scheduler.maintain();
        assertEquals(version1, tester.controller().osVersionTarget(cloud).get().osVersion().version());
    }

    @Test
    public void schedule_stable_release() {
        ControllerTester tester = new ControllerTester();
        Instant t0 = Instant.parse("2021-06-21T07:00:00.00Z"); // Inside trigger period
        tester.clock().setInstant(t0);

        // Set initial target
        CloudName cloud = tester.controller().clouds().iterator().next();
        Version version0 = Version.fromString("8.0");
        tester.controller().upgradeOsIn(cloud, version0, Duration.ZERO, false);

        // Stable release is scheduled immediately
        Version version1 = Version.fromString("8.1");
        tester.serviceRegistry().artifactRepository().addRelease(new OsRelease(version1, OsRelease.Tag.stable,
                                                                               tester.clock().instant()));
        scheduleUpgradeAfter(Duration.ZERO, version1, tester);

        // A newer version is triggered manually
        Version version3 = Version.fromString("8.3");
        tester.controller().upgradeOsIn(cloud, version3, Duration.ZERO, false);

        // Nothing happens in next iteration as tagged release is older than manually triggered version
        scheduleUpgradeAfter(Duration.ofDays(7), version3, tester);
    }

    @Test
    public void schedule_latest_release_in_cd() {
        ControllerTester tester = new ControllerTester(SystemName.cd);
        Instant t0 = Instant.parse("2021-06-21T07:00:00.00Z"); // Inside trigger period
        tester.clock().setInstant(t0);

        // Set initial target
        CloudName cloud = tester.controller().clouds().iterator().next();
        Version version0 = Version.fromString("8.0");
        tester.controller().upgradeOsIn(cloud, version0, Duration.ZERO, false);

        // Latest release is not scheduled immediately
        Version version1 = Version.fromString("8.1");
        tester.serviceRegistry().artifactRepository().addRelease(new OsRelease(version1, OsRelease.Tag.latest,
                                                                               tester.clock().instant()));
        scheduleUpgradeAfter(Duration.ZERO, version0, tester);

        // Cooldown period passes and latest release is scheduled
        scheduleUpgradeAfter(Duration.ofDays(1), version1, tester);
    }

    private void scheduleUpgradeAfter(Duration duration, Version version, ControllerTester tester) {
        tester.clock().advance(duration);
        new OsUpgradeScheduler(tester.controller(), Duration.ofDays(1)).maintain();
        CloudName cloud = tester.controller().clouds().iterator().next();
        OsVersionTarget target = tester.controller().osVersionTarget(cloud).get();
        assertEquals(version, target.osVersion().version());
        assertEquals("No budget when scheduling a tagged release",
                     Duration.ZERO, target.upgradeBudget());
    }

    private static ZoneApi zone(String id, CloudName cloud) {
        return ZoneApiMock.newBuilder().withId(id).with(cloud).build();
    }

}
