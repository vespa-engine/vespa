// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;

import java.time.Instant;
import java.util.Objects;

/**
 * Version information for a node allocated to a {@link com.yahoo.vespa.hosted.controller.application.SystemApplication}.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class NodeVersion {

    private final HostName hostname;
    private final ZoneId zone;
    private final Version currentVersion;
    private final Version wantedVersion;
    private final Instant changedAt;

    public NodeVersion(HostName hostname, ZoneId zone, Version currentVersion, Version wantedVersion, Instant changedAt) {
        this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
        this.currentVersion = Objects.requireNonNull(currentVersion, "version must be non-null");
        this.wantedVersion = Objects.requireNonNull(wantedVersion, "wantedVersion must be non-null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must be non-null");
    }

    /** Hostname of this */
    public HostName hostname() {
        return hostname;
    }

    /** Zone of this */
    public ZoneId zone() {
        return zone;
    }

    /** Current version of this */
    public Version currentVersion() {
        return currentVersion;
    }

    /** Wanted version of this */
    public Version wantedVersion() {
        return wantedVersion;
    }

    /** Returns whether this is changing (upgrading or downgrading) */
    public boolean changing() {
        return !currentVersion.equals(wantedVersion);
    }

    /** The most recent time the version of this changed */
    public Instant changedAt() {
        return changedAt;
    }

    /** Returns a copy of this with current version set to given version */
    public NodeVersion withCurrentVersion(Version version, Instant changedAt) {
        if (currentVersion.equals(version)) return this;
        return new NodeVersion(hostname, zone, version, wantedVersion, changedAt);
    }

    /** Returns a copy of this with wanted version set to given version */
    public NodeVersion withWantedVersion(Version version) {
        if (wantedVersion.equals(version)) return this;
        return new NodeVersion(hostname, zone, currentVersion, version, changedAt);
    }

    @Override
    public String toString() {
        return hostname + ": " + currentVersion + " -> " + wantedVersion + " [zone=" + zone + ", changedAt=" + changedAt + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeVersion that = (NodeVersion) o;
        return hostname.equals(that.hostname) &&
               zone.equals(that.zone) &&
               currentVersion.equals(that.currentVersion) &&
               wantedVersion.equals(that.wantedVersion) &&
               changedAt.equals(that.changedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, zone, currentVersion, wantedVersion, changedAt);
    }

}
