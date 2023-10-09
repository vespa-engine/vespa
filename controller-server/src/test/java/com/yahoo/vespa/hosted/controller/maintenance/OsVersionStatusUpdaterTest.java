// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        CloudName cloud0 = CloudName.DEFAULT;
        tester.controller().os().upgradeTo(version1, cloud0, false, false);
        statusUpdater.maintain();

        var osVersions = tester.controller().os().status().versions();
        assertEquals(3, osVersions.size());
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, cloud0)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, cloud0)).isEmpty(), "No nodes on current target");

        CloudName cloud1 = CloudName.AWS;
        Version version2 = Version.fromString("7.0");
        tester.controller().os().upgradeTo(version2, cloud1, false, false);
        statusUpdater.maintain();

        osVersions = tester.controller().os().status().versions();
        assertEquals(4, osVersions.size()); // 2 in cloud, 2 in otherCloud.
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, cloud0)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version1, cloud0)).isEmpty(), "No nodes on current target");
        assertFalse(osVersions.get(new OsVersion(Version.emptyVersion, cloud1)).isEmpty(), "All nodes on unknown version");
        assertTrue(osVersions.get(new OsVersion(version2, cloud1)).isEmpty(), "No nodes on current target");

        // Updating status cleans up stale certifications
        Set<OsVersion> knownVersions = osVersions.keySet().stream()
                                                 .filter(osVersion -> !osVersion.version().isEmpty())
                                                 .collect(Collectors.toSet());
        // Known versions
        for (OsVersion version : knownVersions) {
            tester.controller().os().certify(version.version(), version.cloud(), Version.fromString("1.2.3"));
        }
        // Additional versions
        OsVersion staleVersion0 = new OsVersion(Version.fromString("7.0"), cloud0);        // Only removed for this cloud
        OsVersion staleVersion1 = new OsVersion(Version.fromString("3.11"), cloud0);       // Stale in both clouds
        OsVersion staleVersion2 = new OsVersion(Version.fromString("3.11"), cloud1);
        OsVersion futureVersion = new OsVersion(Version.fromString("98.0.2"), cloud0);     // Keep future version
        for (OsVersion version : List.of(staleVersion0, staleVersion1, staleVersion2, futureVersion)) {
            tester.controller().os().certify(version.version(), version.cloud(), Version.fromString("1.2.3"));
        }
        statusUpdater.maintain();
        assertEquals(Stream.concat(knownVersions.stream(), Stream.of(futureVersion)).sorted().toList(),
                     certifiedOsVersions(tester));
    }

    private static List<OsVersion> certifiedOsVersions(ControllerTester tester) {
        return tester.controller().os().readCertified().stream()
                     .map(CertifiedOsVersion::osVersion)
                     .sorted()
                     .toList();
    }

}
