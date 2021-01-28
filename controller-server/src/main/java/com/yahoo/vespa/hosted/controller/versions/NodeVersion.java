// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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
    private final Optional<Instant> suspendedAt;

    public NodeVersion(HostName hostname, ZoneId zone, Version currentVersion, Version wantedVersion,
                       Optional<Instant> suspendedAt) {
        this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
        this.currentVersion = Objects.requireNonNull(currentVersion, "version must be non-null");
        this.wantedVersion = Objects.requireNonNull(wantedVersion, "wantedVersion must be non-null");
        this.suspendedAt = Objects.requireNonNull(suspendedAt, "suspendedAt must be non-null");
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

    /** Returns the duration of the change in this, measured relative to instant */
    public Duration changeDuration(Instant instant) {
        if (!upgrading()) return Duration.ZERO;
        if (suspendedAt.isEmpty()) return Duration.ZERO; // Node hasn't suspended to apply the change yet
        return Duration.between(suspendedAt.get(), instant).abs();
    }

    /** The most recent time the node referenced by this suspended. This is empty if the node is not suspended. */
    public Optional<Instant> suspendedAt() {
        return suspendedAt;
    }

    @Override
    public String toString() {
        return hostname + ": " + currentVersion + " -> " + wantedVersion + " [zone=" + zone + ", suspendedAt=" + suspendedAt.map(Instant::toString).orElse("<not suspended>") + "]";
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
               suspendedAt.equals(that.suspendedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, zone, currentVersion, wantedVersion, suspendedAt);
    }

    /** Returns whether this is upgrading */
    private boolean upgrading() {
        return currentVersion.isBefore(wantedVersion);
    }

}
