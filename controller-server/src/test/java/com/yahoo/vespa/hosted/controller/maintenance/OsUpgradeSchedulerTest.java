// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class OsUpgradeSchedulerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        OsUpgradeScheduler scheduler = new OsUpgradeScheduler(tester.controller(), Duration.ofDays(1));
        Instant initialTime = Instant.parse("2021-01-23T00:00:00.00Z");
        tester.clock().setInstant(initialTime);

        CloudName cloud = CloudName.from("cloud");
        ZoneApi zone = zone("prod.us-west-1", cloud);
        tester.zoneRegistry().setZones(zone).reprovisionToUpgradeOsIn(zone);

        // Initial run does nothing as the cloud does not have a target
        scheduler.maintain();
        assertTrue("No target set", tester.controller().osVersionTarget(cloud).isEmpty());

        // Target is set
        Version version0 = Version.fromString("7.0.0.20210123190005");
        tester.controller().upgradeOsIn(cloud, version0, Optional.of(Duration.ofDays(1)), false);

        // Target remains unchanged as it hasn't expired yet
        for (var interval : List.of(Duration.ZERO, Duration.ofDays(15))) {
            tester.clock().advance(interval);
            scheduler.maintain();
            assertEquals(version0, tester.controller().osVersionTarget(cloud).get().osVersion().version());
        }

        // Just over 30 days pass, and a new target replaces the expired one
        Version version1 = Version.fromString("7.0.0.20210215");
        tester.clock().advance(Duration.ofDays(15).plus(Duration.ofSeconds(1)));
        scheduler.maintain();
        assertEquals("New target set", version1, tester.controller().osVersionTarget(cloud).get().osVersion().version());

        // A few days pass and target remains unchanged
        tester.clock().advance(Duration.ofDays(2));
        scheduler.maintain();
        assertEquals(version1, tester.controller().osVersionTarget(cloud).get().osVersion().version());
    }

    private static ZoneApi zone(String id, CloudName cloud) {
        return ZoneApiMock.newBuilder().withId(id).with(cloud).build();
    }

}
