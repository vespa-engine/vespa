// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.version;

import com.yahoo.component.Version;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class CuratorVersionStateTest {

    private final MockCurator curator = new MockCurator();

    @Test
    public void storedVersion() {
        CuratorVersionState state = new CuratorVersionState(curator, new Version(8, 100, 1));
        assertEquals(Version.emptyVersion, state.storedVersion());

        curator.set(CuratorVersionState.versionPath, "8.99.0".getBytes());
        assertEquals(new Version(8, 99, 0), state.storedVersion());
    }

    @Test
    public void storedVersionWithInvalidData() {
        curator.set(CuratorVersionState.versionPath, "not-a-version".getBytes());
        CuratorVersionState state = new CuratorVersionState(curator, new Version(8, 100, 1));
        assertEquals(Version.emptyVersion, state.storedVersion());
    }

    @Test
    public void isUpgraded() {
        CuratorVersionState state = new CuratorVersionState(curator, new Version(8, 100, 1));

        // No stored version => upgraded
        assertTrue(state.isUpgraded());

        // Stored version < current => upgraded
        curator.set(CuratorVersionState.versionPath, "8.99.0".getBytes());
        assertTrue(state.isUpgraded());

        // Stored version == current => not upgraded
        curator.set(CuratorVersionState.versionPath, "8.100.1".getBytes());
        assertFalse(state.isUpgraded());

        // Stored version > current => not upgraded
        curator.set(CuratorVersionState.versionPath, "8.200.0".getBytes());
        assertFalse(state.isUpgraded());
    }

}
