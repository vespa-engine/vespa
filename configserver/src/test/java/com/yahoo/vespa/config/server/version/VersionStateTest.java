// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.version;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class VersionStateTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();
    private final MockCurator curator = new MockCurator();

    @Test
    public void upgrade() throws IOException {
        Version unknownVersion = new Version(0, 0, 0);

        VersionState state = createVersionState();
        assertEquals(unknownVersion, state.storedVersion());
        assertTrue(state.isUpgraded());
        state.storeCurrentVersion();
        assertFalse(state.isUpgraded());

        state.storeVersion("badversion");
        assertEquals(unknownVersion, state.storedVersion());
        assertTrue(state.isUpgraded());

        state.storeVersion("5.0.0");
        assertEquals(new Version(5, 0, 0), state.storedVersion());
        assertTrue(state.isUpgraded());

        // Remove zk node, should find version in ZooKeeper
        curator.delete(VersionState.versionPath);
        assertEquals(new Version(5, 0, 0), state.storedVersion());
        assertTrue(state.isUpgraded());

        // Save new version, remove version in file, should find version in ZooKeeper
        state.storeVersion("6.0.0");
        Files.delete(state.versionFile().toPath());
        assertEquals(new Version(6, 0, 0), state.storedVersion());
        assertTrue(state.isUpgraded());

        state.storeCurrentVersion();
        assertEquals(state.currentVersion(), state.storedVersion());
        assertFalse(state.isUpgraded());
    }

    @Test
    public void serverdbfile() throws IOException {
        File dbDir = tempDir.newFolder();
        VersionState state = new VersionState(new ConfigserverConfig.Builder().configServerDBDir(dbDir.getAbsolutePath()).build(), curator);
        state.storeCurrentVersion();
        File versionFile = new File(dbDir, "vespa_version");
        assertTrue(versionFile.exists());
        Version stored = Version.fromString(IOUtils.readFile(versionFile));
        assertEquals(stored, state.currentVersion());
    }

    private VersionState createVersionState() throws IOException {
        return new VersionState(tempDir.newFile(), curator, true);
    }

}
