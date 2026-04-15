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
public class VespaVersionStateTest {

    private final MockCurator curator = new MockCurator();

    @Test
    public void storedVersion() {
        VespaVersionState state = new VespaVersionState(curator, new Version(8, 100, 1));
        assertEquals(Version.emptyVersion, state.storedVersion());

        curator.set(VespaVersionState.versionPath, "8.99.0".getBytes());
        assertEquals(new Version(8, 99, 0), state.storedVersion());
    }

    @Test
    public void storedVersionWithInvalidData() {
        curator.set(VespaVersionState.versionPath, "not-a-version".getBytes());
        VespaVersionState state = new VespaVersionState(curator, new Version(8, 100, 1));
        assertEquals(Version.emptyVersion, state.storedVersion());
    }

    @Test
    public void isUpgrading() {
        VespaVersionState state = new VespaVersionState(curator, new Version(8, 100, 1));

        // No stored version => upgraded
        assertTrue(state.isUpgrading());

        // Stored version < current => upgraded
        curator.set(VespaVersionState.versionPath, "8.99.0".getBytes());
        assertTrue(state.isUpgrading());

        // Stored version == current => not upgraded
        curator.set(VespaVersionState.versionPath, "8.100.1".getBytes());
        assertFalse(state.isUpgrading());

        // Stored version > current => not upgraded
        curator.set(VespaVersionState.versionPath, "8.200.0".getBytes());
        assertFalse(state.isUpgrading());
    }

}
