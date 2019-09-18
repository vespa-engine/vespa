// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class OsVersionsTest {

    @Test
    public void test_versions() {
        var versions = new OsVersions(new NodeRepositoryTester().nodeRepository().database(), Duration.ofDays(1));

        assertTrue("No versions set", versions.targets().isEmpty());
        assertSame("Caches empty target versions", versions.targets(), versions.targets());

        // Upgrade OS
        var version1 = new OsVersion(Version.fromString("7.1"), false);
        versions.setTarget(NodeType.host, version1.version(), false);
        var targetVersions = versions.targets();
        assertSame("Caches target versions", targetVersions, versions.targets());
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Upgrade OS again
        var version2 = new OsVersion(Version.fromString("7.2"), false);
        versions.setTarget(NodeType.host, version2.version(), false);
        assertNotSame("Cache invalidated", targetVersions, versions.targets());
        assertEquals(version2, versions.targetFor(NodeType.host).get());

        // Target can be (de)activated
        versions.setActive(NodeType.host, true);
        assertTrue("Target version activated", versions.targetFor(NodeType.host).get().active());

        // Re-setting the same version does not affect active status
        versions.setTarget(NodeType.host, version2.version(), false);
        assertTrue("Target version remains active", versions.targetFor(NodeType.host).get().active());

        versions.setActive(NodeType.host, false);
        assertFalse("Target version deactivated", versions.targetFor(NodeType.host).get().active());

        // Downgrading fails
        try {
            versions.setTarget(NodeType.host, version1.version(), false);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        // Forcing downgrade succeeds
        versions.setTarget(NodeType.host, version1.version(), true);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Target can be removed
        versions.removeTarget(NodeType.host);
        assertFalse(versions.targetFor(NodeType.host).isPresent());
    }

}
