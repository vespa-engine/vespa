// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class OsVersionStatusUpdaterTest {

    @Test
    public void test_update() {
        ControllerTester tester = new ControllerTester();
        OsVersionStatusUpdater statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1)
        );
        // Add all zones to upgrade policy
        UpgradePolicy upgradePolicy = UpgradePolicy.create();
        for (ZoneApi zone : tester.zoneRegistry().zones().controllerUpgraded().zones()) {
            upgradePolicy = upgradePolicy.upgrade(zone);
        }
        tester.zoneRegistry().setOsUpgradePolicy(CloudName.defaultName(), upgradePolicy);

        // Initially empty
        assertSame(OsVersionStatus.empty, tester.controller().osVersionStatus());

        // Setting a new target adds it to current status
        Version version1 = Version.fromString("7.1");
        CloudName cloud = CloudName.defaultName();
        tester.controller().upgradeOsIn(cloud, version1, Duration.ZERO, false);
        statusUpdater.maintain();

        var osVersions = tester.controller().osVersionStatus().versions();
        assertEquals(2, osVersions.size());
        assertFalse("All nodes on unknown version", osVersions.get(new OsVersion(Version.emptyVersion, cloud)).isEmpty());
        assertTrue("No nodes on current target", osVersions.get(new OsVersion(version1, cloud)).isEmpty());
    }

}
