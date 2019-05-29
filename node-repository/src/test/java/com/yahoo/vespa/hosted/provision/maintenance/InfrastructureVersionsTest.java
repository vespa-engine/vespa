// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author freva
 */
public class InfrastructureVersionsTest {

    private final Version defaultVersion = Version.fromString("6.13.37");
    private final NodeRepositoryTester tester = new NodeRepositoryTester();
    private final InfrastructureVersions infrastructureVersions =
            new InfrastructureVersions(tester.nodeRepository().database(), NodeType.config, defaultVersion);

    private final Version version = Version.fromString("6.123.456");

    @Test
    public void can_only_downgrade_with_force() {
        assertTrue(infrastructureVersions.getTargetVersions().isEmpty());

        assertEquals(defaultVersion, infrastructureVersions.getTargetVersionFor(NodeType.config));
        infrastructureVersions.setTargetVersion(NodeType.config, version, false);
        assertEquals(version, infrastructureVersions.getTargetVersionFor(NodeType.config));

        // Upgrading to new version without force is fine
        Version newVersion = Version.fromString("6.123.457"); // version + 1
        infrastructureVersions.setTargetVersion(NodeType.config, newVersion, false);
        assertEquals(newVersion, infrastructureVersions.getTargetVersionFor(NodeType.config));

        // Downgrading to old version without force fails
        assertThrows(IllegalArgumentException.class,
                () -> infrastructureVersions.setTargetVersion(NodeType.config, version, false));

        infrastructureVersions.setTargetVersion(NodeType.config, version, true);
        assertEquals(version, infrastructureVersions.getTargetVersionFor(NodeType.config));
    }

    @Test
    public void can_only_set_version_on_certain_node_types() {
        // We can set version for config
        infrastructureVersions.setTargetVersion(NodeType.config, version, false);

        assertThrows(IllegalArgumentException.class,
                () -> infrastructureVersions.setTargetVersion(NodeType.tenant, version, false));

        // Using 'force' does not help, force only applies to version downgrade
        assertThrows(IllegalArgumentException.class,
                () -> infrastructureVersions.setTargetVersion(NodeType.tenant, version, true));
    }

    @Test
    public void store_all_valid_for_config() {
        infrastructureVersions.setTargetVersion(NodeType.config, version, false);
        infrastructureVersions.setTargetVersion(NodeType.confighost, version, false);
        infrastructureVersions.setTargetVersion(NodeType.proxyhost, version, false);

        assertThrows(IllegalArgumentException.class,
                () -> infrastructureVersions.setTargetVersion(NodeType.controller, version, false));
        assertThrows(IllegalArgumentException.class,
                () -> infrastructureVersions.setTargetVersion(NodeType.controllerhost, version, false));

        Map<NodeType, Version> expected = Map.of(
                NodeType.config, version,
                NodeType.confighost, version,
                NodeType.proxyhost, version);

        assertEquals(expected, infrastructureVersions.getTargetVersions());
    }

    @Test
    public void store_all_valid_for_controller() {
        InfrastructureVersions infrastructureVersions =
                new InfrastructureVersions(tester.nodeRepository().database(), NodeType.controller, defaultVersion);

        infrastructureVersions.setTargetVersion(NodeType.controller, version, false);
        infrastructureVersions.setTargetVersion(NodeType.controllerhost, version, false);
        infrastructureVersions.setTargetVersion(NodeType.proxyhost, version, false);

        assertThrows(IllegalArgumentException.class,
                () -> infrastructureVersions.setTargetVersion(NodeType.config, version, false));
        assertThrows(IllegalArgumentException.class,
                () -> infrastructureVersions.setTargetVersion(NodeType.confighost, version, false));

        Map<NodeType, Version> expected = Map.of(
                NodeType.controller, version,
                NodeType.controllerhost, version,
                NodeType.proxyhost, version);

        assertEquals(expected, infrastructureVersions.getTargetVersions());
    }

    private static void assertThrows(Class<? extends Throwable> clazz, Runnable runnable) {
        try {
            runnable.run();
            fail("Expected " + clazz);
        } catch (Throwable e) {
            if (!clazz.isInstance(e)) throw e;
        }
    }
}
