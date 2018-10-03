// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.version;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.Version;
import com.yahoo.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

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

    @Test
    public void upgrade() throws IOException {
        Version unknownVersion = Version.fromIntValues(0, 0, 0);
        File versionFile = tempDir.newFile();
        VersionState state = new VersionState(versionFile);
        assertThat(state.storedVersion(), is(unknownVersion));
        assertTrue(state.isUpgraded());
        state.saveNewVersion();
        assertFalse(state.isUpgraded());

        IOUtils.writeFile(versionFile, "badversion", false);
        assertThat(state.storedVersion(), is(unknownVersion));
        assertTrue(state.isUpgraded());

        IOUtils.writeFile(versionFile, "5.0.0", false);
        assertThat(state.storedVersion(), is(Version.fromIntValues(5, 0, 0)));
        assertTrue(state.isUpgraded());

        state.saveNewVersion();
        assertThat(state.currentVersion(), is(state.storedVersion()));
        assertFalse(state.isUpgraded());
    }

    @Test
    public void serverdbfile() throws IOException {
        File dbDir = tempDir.newFolder();
        VersionState state = new VersionState(new ConfigserverConfig(new ConfigserverConfig.Builder().configServerDBDir(dbDir.getAbsolutePath())));
        state.saveNewVersion();
        File versionFile = new File(dbDir, "vespa_version");
        assertTrue(versionFile.exists());
        Version stored = Version.fromString(IOUtils.readFile(versionFile));
        assertThat(stored, is(state.currentVersion()));
    }
}
