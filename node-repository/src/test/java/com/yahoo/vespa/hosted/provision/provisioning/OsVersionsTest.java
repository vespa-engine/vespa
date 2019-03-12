// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;

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

    private final OsVersions versions = new OsVersions(
                new NodeRepositoryTester().nodeRepository().database(),
                Duration.ofDays(1) // Long TTL to avoid timed expiry during test
        );

    @Test
    public void test_versions() {
        assertTrue("No versions set", versions.targets().isEmpty());
        assertSame("Caches empty target versions", versions.targets(), versions.targets());

        // Upgrade OS
        Version version1 = Version.fromString("7.1");
        versions.setTarget(NodeType.host, version1, false);
        Map<NodeType, Version> targetVersions = versions.targets();
        assertSame("Caches target versions", targetVersions, versions.targets());
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Upgrade OS again
        Version version2 = Version.fromString("7.2");
        versions.setTarget(NodeType.host, version2, false);
        assertNotSame("Cache invalidated", targetVersions, versions.targets());
        assertEquals(version2, versions.targetFor(NodeType.host).get());

        // Downgrading fails
        try {
            versions.setTarget(NodeType.host, version1, false);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        // Forcing downgrade succeeds
        versions.setTarget(NodeType.host, version1, true);
        assertEquals(version1, versions.targetFor(NodeType.host).get());

        // Target can be removed
        versions.removeTarget(NodeType.host);
        assertFalse(versions.targetFor(NodeType.host).isPresent());
    }

}
