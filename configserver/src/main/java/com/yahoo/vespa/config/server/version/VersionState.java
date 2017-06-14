// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.version;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Contains version information for this configserver.
 *
 * @author lulf
 */
public class VersionState {

    private final File versionFile;

    @Inject
    public VersionState(ConfigserverConfig config) {
        this(new File(Defaults.getDefaults().underVespaHome(config.configServerDBDir()), "vespa_version"));
    }

    public VersionState(File versionFile) {
        this.versionFile = versionFile;
    }

    public boolean isUpgraded() {
        return currentVersion().compareTo(storedVersion()) > 0;
    }

    public void saveNewVersion() {
        try (FileWriter writer = new FileWriter(versionFile)) {
            writer.write(currentVersion().toSerializedForm());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Version storedVersion() {
        try (FileReader reader = new FileReader(versionFile)) {
            return Version.fromString(IOUtils.readAll(reader));
        } catch (Exception e) {
            return Version.fromIntValues(0, 0, 0); // Use an old value to signal we don't know
        }
    }

    public Version currentVersion() {
        return Version.fromIntValues(VespaVersion.major, VespaVersion.minor, VespaVersion.micro);
    }

    @Override
    public String toString() {
        return String.format("Current version:%s, stored version:%s", currentVersion(), storedVersion());
    }

}
