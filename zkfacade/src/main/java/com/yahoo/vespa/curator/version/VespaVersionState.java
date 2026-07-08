// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.version;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;

import java.util.Optional;

/**
 * Provides access to the Vespa version stored in ZooKeeper and the current version of the running process.
 * This can be used to detect version changes (upgrades/downgrades) across restarts.
 *
 * @author hmusum
 */
public class VespaVersionState {

    public static final Path versionPath = Path.fromString("/config/v2/vespa_version");

    private final Curator curator;
    private final Version currentVersion;

    public VespaVersionState(Curator curator) {
        this(curator, Vtag.currentVersion);
    }

    public VespaVersionState(Curator curator, Version currentVersion) {
        this.curator = curator;
        this.currentVersion = currentVersion;
    }

    /** Returns the current version of the running process */
    public Version currentVersion() {
        return currentVersion;
    }

    /** Returns the version stored in ZooKeeper, or {@link Version#emptyVersion} if not found */
    public Version storedVersion() {
        Optional<byte[]> version = curator.getData(versionPath);
        if (version.isPresent()) {
            try {
                return Version.fromString(Utf8.toString(version.get()));
            } catch (Exception e) {
                return Version.emptyVersion;
            }
        }
        return Version.emptyVersion;
    }

    /** Returns whether the current version is newer than the stored version */
    public boolean isUpgrading() {
        Version storedVersion = storedVersion();
        if (storedVersion.equals(Version.emptyVersion)) return true;
        return currentVersion().compareTo(storedVersion) > 0;
    }

    /** Returns whether the current version is different than the stored version */
    public boolean isUpgradingOrDowngrading() {
        Version storedVersion = storedVersion();
        if (storedVersion.equals(Version.emptyVersion)) return true;
        return currentVersion().compareTo(storedVersion) != 0;
    }


    @Override
    public String toString() {
        return "Current version: " + currentVersion() + ", stored version: " + storedVersion();
    }

}
