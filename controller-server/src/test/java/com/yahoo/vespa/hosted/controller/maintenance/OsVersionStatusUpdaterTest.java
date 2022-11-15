// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class OsVersionStatusUpdaterTest {

    @Test
    void test_update() {
        ControllerTester tester = new ControllerTester();
        OsVersionStatusUpdater statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1)
        );
        // Add all zones to upgrade policy
        UpgradePolicy.Builder upgradePolicy = UpgradePolicy.builder();
        for (ZoneApi zone : tester.zoneRegistry().zones().controllerUpgraded().zones()) {
            upgradePolicy = upgradePolicy.upgrade(zone);
        }
        tester.zoneRegistry().setOsUpgradePolicy(CloudName.DEFAULT, upgradePolicy.build());

        // Initially empty
        assertSame(OsVersionStatus.empty, tester.controller().osVersionStatus());

        // Setting a new target adds it to current status
        Version version1 = Version.fromString("7.1");
        CloudName cloud = CloudName.DEFAULT;
        tester.controller().upgradeOsIn(cloud, version1, false);
        statusUpdater.maintain();

        var osVersions = tester.controller().osVersionStatus().versions();
        assertEquals(3, osVersions.size());
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, cloud)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, cloud)).isEmpty(), "No nodes on current target");

        CloudName otherCloud = CloudName.AWS;
        tester.controller().upgradeOsIn(otherCloud, version1, false);
        statusUpdater.maintain();

        osVersions = tester.controller().osVersionStatus().versions();
        assertEquals(4, osVersions.size()); // 2 in cloud, 2 in otherCloud.
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, cloud)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, cloud)).isEmpty(), "No nodes on current target");
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, otherCloud)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, otherCloud)).isEmpty(), "No nodes on current target");
    }

}
