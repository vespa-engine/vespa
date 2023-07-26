// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.versions.CertifiedOsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        OsVersionStatusUpdater statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1));
        // Add all zones to upgrade policy
        UpgradePolicy.Builder upgradePolicy = UpgradePolicy.builder();
        for (ZoneApi zone : tester.zoneRegistry().zones().controllerUpgraded().zones()) {
            upgradePolicy = upgradePolicy.upgrade(zone);
        }
        tester.zoneRegistry().setOsUpgradePolicy(CloudName.DEFAULT, upgradePolicy.build());

        // Initially empty
        assertSame(OsVersionStatus.empty, tester.controller().os().status());

        // Setting a new target adds it to current status
        Version version1 = Version.fromString("7.1");
        CloudName cloud = CloudName.DEFAULT;
        tester.controller().os().upgradeTo(version1, cloud, false, false);
        statusUpdater.maintain();

        var osVersions = tester.controller().os().status().versions();
        assertEquals(3, osVersions.size());
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, cloud)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, cloud)).isEmpty(), "No nodes on current target");

        CloudName otherCloud = CloudName.AWS;
        tester.controller().os().upgradeTo(version1, otherCloud, false, false);
        statusUpdater.maintain();

        osVersions = tester.controller().os().status().versions();
        assertEquals(4, osVersions.size()); // 2 in cloud, 2 in otherCloud.
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, cloud)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, cloud)).isEmpty(), "No nodes on current target");
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, otherCloud)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, otherCloud)).isEmpty(), "No nodes on current target");

        // Updating status cleans up stale certifications
        Set<OsVersion> knownVersions = osVersions.keySet().stream()
                                                 .filter(osVersion -> !osVersion.version().isEmpty())
                                                 .collect(Collectors.toSet());
        List<OsVersion> versionsToCertify = new ArrayList<>(knownVersions);
        versionsToCertify.addAll(List.of(new OsVersion(Version.fromString("95.0.1"), cloud),
                                         new OsVersion(Version.fromString("98.0.2"), cloud)));
        for (OsVersion version : versionsToCertify) {
            tester.controller().os().certify(version.version(), version.cloud(), Version.fromString("1.2.3"));
        }
        assertEquals(knownVersions.size() + 2, certifiedOsVersions(tester).size());
        statusUpdater.maintain();
        assertEquals(knownVersions, certifiedOsVersions(tester));
    }

    private static Set<OsVersion> certifiedOsVersions(ControllerTester tester) {
        return tester.controller().os().readCertified().stream()
                     .map(CertifiedOsVersion::osVersion)
                     .collect(Collectors.toSet());
    }

}
