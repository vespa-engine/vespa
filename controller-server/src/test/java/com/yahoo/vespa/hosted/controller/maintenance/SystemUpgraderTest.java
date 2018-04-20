// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.zone.UpgradePolicy;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class SystemUpgraderTest {

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void upgrade_system() {
        ZoneId zone1 = ZoneId.from("prod", "eu-west-1");
        ZoneId zone2 = ZoneId.from("prod", "us-west-1");
        ZoneId zone3 = ZoneId.from("prod", "us-central-1");
        ZoneId zone4 = ZoneId.from("prod", "us-east-3");

        tester.controllerTester().zoneRegistry().setUpgradePolicy(
                UpgradePolicy.create()
                             .upgrade(zone1)
                             .upgradeInParallel(zone2, zone3)
                             .upgrade(zone4)
        );

        Version version1 = Version.fromString("6.5");
        tester.updateVersionStatus(version1);
        tester.systemUpgrader().maintain();
        assertTrue("Zones are on current version", onVersion(version1, zone1, zone2, zone3, zone4));

        // Controller upgrades
        Version version2 = Version.fromString("6.6");
        tester.upgradeController(version2);
        assertEquals(version2, tester.controller().versionStatus().controllerVersion().get().versionNumber());

        // System upgrade scheduled. First zone starts upgrading
        tester.systemUpgrader().maintain();
        assertWantedVersion(version2, zone1);
        assertWantedVersion(version1, zone2, zone3, zone4); // Other zones remains on previous version

        // Zones 1 completes upgrade
        completeUpgrade(version2, zone1);

        // Zones 2 and 3 upgrade in parallel
        tester.systemUpgrader().maintain();
        assertWantedVersion(version2, zone2, zone3);
        assertWantedVersion(version1, zone4);
        completeUpgrade(version2, zone2, zone3);

        // Zone 4 upgrades last
        tester.systemUpgrader().maintain();
        assertWantedVersion(version2, zone4);
        completeUpgrade(version2, zone4);

        // Next run does nothing as system is now upgraded
        tester.systemUpgrader().maintain();
        assertWantedVersion(version2, zone1, zone2, zone3, zone4);
        assertTrue(onVersion(version2, zone1, zone2, zone3, zone4));
    }

    @Test
    public void never_downgrades_system() {
        ZoneId zone = ZoneId.from("prod", "eu-west-1");
        tester.controllerTester().zoneRegistry().setUpgradePolicy(UpgradePolicy.create().upgrade(zone));

        Version version = Version.fromString("6.5");
        tester.updateVersionStatus(version);
        tester.systemUpgrader().maintain();
        assertTrue("Zone is on current version", onVersion(version, zone));

        // Controller is downgraded
        tester.upgradeController(Version.fromString("6.4"));

        // Wanted version for zone remains unchanged
        tester.systemUpgrader().maintain();
        assertWantedVersion(version, zone);
    }

    private void completeUpgrade(Version version, ZoneId... zones) {
        for (ZoneId zone : zones) {
            for (URI configServer : tester.controller().zoneRegistry().getConfigServerUris(zone)) {
                tester.controllerTester().configServer().versions().put(configServer, version);
            }
            assertTrue(onVersion(version, zone));
        }
    }

    private void assertWantedVersion(Version version, ZoneId... zones) {
        for (ZoneId zone : zones) {
            for (URI configServer : tester.controller().zoneRegistry().getConfigServerUris(zone)) {
                assertEquals(version, tester.controller().configServer().version(configServer).wanted());
            }
        }
    }

    private boolean onVersion(Version version, ZoneId... zone) {
        return Arrays.stream(zone)
                     .flatMap(z -> tester.controller().zoneRegistry().getConfigServerUris(z).stream())
                     .allMatch(uri -> tester.controller().configServer().version(uri).current().equals(version));
    }

}
