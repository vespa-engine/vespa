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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
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
        assertThat(state.storedVersion(), is(unknownVersion));
        assertTrue(state.isUpgraded());
        state.saveNewVersion();
        assertFalse(state.isUpgraded());

        state.saveNewVersion("badversion");
        assertThat(state.storedVersion(), is(unknownVersion));
        assertTrue(state.isUpgraded());

        state.saveNewVersion("5.0.0");
        assertThat(state.storedVersion(), is(new Version(5, 0, 0)));
        assertTrue(state.isUpgraded());

        // Remove zk node, should find version in ZooKeeper
        curator.delete(VersionState.versionPath);
        assertThat(state.storedVersion(), is(new Version(5, 0, 0)));
        assertTrue(state.isUpgraded());

        // Save new version, remove version in file, should find version in ZooKeeper
        state.saveNewVersion("6.0.0");
        Files.delete(state.versionFile().toPath());
        assertThat(state.storedVersion(), is(new Version(6, 0, 0)));
        assertTrue(state.isUpgraded());

        state.saveNewVersion();
        assertThat(state.currentVersion(), is(state.storedVersion()));
        assertFalse(state.isUpgraded());
    }

    @Test
    public void serverdbfile() throws IOException {
        File dbDir = tempDir.newFolder();
        VersionState state = new VersionState(new ConfigserverConfig.Builder().configServerDBDir(dbDir.getAbsolutePath()).build(), curator);
        state.saveNewVersion();
        File versionFile = new File(dbDir, "vespa_version");
        assertTrue(versionFile.exists());
        Version stored = Version.fromString(IOUtils.readFile(versionFile));
        assertThat(stored, is(state.currentVersion()));
    }

    private VersionState createVersionState() throws IOException {
        return new VersionState(tempDir.newFile(), curator);
    }

}
