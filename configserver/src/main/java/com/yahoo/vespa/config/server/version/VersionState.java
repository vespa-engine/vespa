// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.version;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;

/**
 *
 * Contains version information for this configserver. Stored both in file system and in ZooKeeper (uses
 * data in ZooKeeper if distributeApplicationPackage and data found in ZooKeeper)
 *
 * @author Ulf Lilleengen
 */
public class VersionState {

    static final Path versionPath = Path.fromString("/config/v2/vespa_version");

    private final File versionFile;
    private final Curator curator;

    @Inject
    public VersionState(ConfigserverConfig config, Curator curator) {
        this(new File(Defaults.getDefaults().underVespaHome(config.configServerDBDir()), "vespa_version"), curator);
    }

    public VersionState(File versionFile, Curator curator) {
        this.versionFile = versionFile;
        this.curator = curator;
    }

    public boolean isUpgraded() {
        return currentVersion().compareTo(storedVersion()) > 0;
    }

    public void saveNewVersion() {
        saveNewVersion(currentVersion().toFullString());
    }

    public void saveNewVersion(String vespaVersion) {
        curator.set(versionPath, Utf8.toBytes(vespaVersion));
        try (FileWriter writer = new FileWriter(versionFile)) {
            writer.write(vespaVersion);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Version storedVersion() {
        Optional<byte[]> version = curator.getData(versionPath);
        if (version.isPresent()) {
            try {
                return Version.fromString(Utf8.toString(version.get()));
            } catch (Exception e) {
                // continue, use value in file
            }
        }
        try (FileReader reader = new FileReader(versionFile)) {
            return Version.fromString(IOUtils.readAll(reader));
        } catch (Exception e) {
            return Version.emptyVersion;
        }
    }

    public Version currentVersion() {
        return new Version(VespaVersion.major, VespaVersion.minor, VespaVersion.micro);
    }

    File versionFile() {
        return versionFile;
    }

    @Override
    public String toString() {
        return String.format("Current version:%s, stored version:%s", currentVersion(), storedVersion());
    }

}
