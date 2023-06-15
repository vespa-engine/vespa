// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.version;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
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
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 *
 * Contains version information for this configserver. Stored both in file system and in ZooKeeper (uses
 * data in ZooKeeper if distributeApplicationPackage and data found in ZooKeeper)
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class VersionState {

    private static final Logger log = Logger.getLogger(VersionState.class.getName());
    private static final int allowedMinorVersionInterval = 30; // (2 months of releases => ~30 releases)
    private static final Version latestVersionOnPreviousMajor = Version.fromString("7.594.36");
    static final Path versionPath = Path.fromString("/config/v2/vespa_version");

    private final File versionFile;
    private final Curator curator;
    private final Version currentVersion;
    private final boolean skipUpgradeCheck;

    @Inject
    public VersionState(ConfigserverConfig config, Curator curator) {
        this(new File(Defaults.getDefaults().underVespaHome(config.configServerDBDir()), "vespa_version"),
             curator,
             Boolean.parseBoolean(Optional.ofNullable(System.getenv("VESPA_SKIP_UPGRADE_CHECK")).orElse("false")));
    }

    public VersionState(File versionFile, Curator curator, boolean skipUpgradeCheck) {
        this(versionFile,
             curator,
             new Version(VespaVersion.major, VespaVersion.minor, VespaVersion.micro),
             skipUpgradeCheck);
    }

    public VersionState(File versionFile,
                        Curator curator,
                        Version currentVersion,
                        boolean skipUpgradeCheck) {
        this.versionFile = versionFile;
        this.curator = curator;
        this.currentVersion = currentVersion;
        this.skipUpgradeCheck = skipUpgradeCheck;
    }

    public boolean isUpgraded() {
        Version storedVersion = storedVersion();
        if (storedVersion.equals(Version.emptyVersion)) return true;

        // TODO: Also verify version for downgrades?
        if (currentVersion().compareTo(storedVersion) > 0) {
            verifyVersionIntervalForUpgrade(storedVersion);
            return true;
        } else {
            return false;
        }
    }

    public void storeCurrentVersion() {
        storeVersion(currentVersion().toFullString());
    }

    public void storeVersion(String vespaVersion) {
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
        return currentVersion;
    }

    File versionFile() {
        return versionFile;
    }

    @Override
    public String toString() {
        return String.format("Current version:%s, stored version:%s", currentVersion(), storedVersion());
    }

    private void verifyVersionIntervalForUpgrade(Version storedVersion) {
        int storedVersionMajor = storedVersion.getMajor();
        int storedVersionMinor = storedVersion.getMinor();
        int currentVersionMajor = currentVersion.getMajor();
        int currentVersionMinor = currentVersion.getMinor();
        boolean sameMajor = storedVersionMajor == currentVersionMajor;
        boolean differentMajor = !sameMajor;

        String message = "Cannot upgrade from " + storedVersion + " to " + currentVersion();
        if (storedVersionMajor < latestVersionOnPreviousMajor.getMajor())
            logOrThrow(message + " (upgrade across 2 major versions not supported). Please upgrade to " +
                               latestVersionOnPreviousMajor.toFullString() + " first." +
                               " Setting VESPA_SKIP_UPGRADE_CHECK=true will skip this check at your own risk," +
                               " see https://vespa.ai/releases.html#versions");
        else if (sameMajor && (currentVersionMinor - storedVersionMinor > allowedMinorVersionInterval))
            logOrThrow(message + ". Please upgrade to an older version first, the interval between the two versions is too large (> " + allowedMinorVersionInterval + " releases)." +
                               " Setting VESPA_SKIP_UPGRADE_CHECK=true will skip this check at your own risk," +
                               " see https://vespa.ai/releases.html#versions");
        else if (differentMajor && storedVersionMinor < latestVersionOnPreviousMajor.getMinor())
            logOrThrow(message + " (new major version). Please upgrade to " + latestVersionOnPreviousMajor.toFullString() + " first." +
                               " Setting VESPA_SKIP_UPGRADE_CHECK=true will skip this check at your own risk," +
                               " see https://vespa.ai/releases.html#versions");
        else if (differentMajor && currentVersionMinor > allowedMinorVersionInterval)
            logOrThrow(message + ". Please upgrade to an older version first, the interval between the two versions is too large (> " + allowedMinorVersionInterval + " releases)." +
                               " Setting VESPA_SKIP_UPGRADE_CHECK=true will skip this check at your own risk," +
                               " see https://vespa.ai/releases.html#versions");
    }

    private void logOrThrow(String message) {
        if (skipUpgradeCheck)
            log.log(WARNING, message);
        else
            throw new RuntimeException(message);
    }

}
