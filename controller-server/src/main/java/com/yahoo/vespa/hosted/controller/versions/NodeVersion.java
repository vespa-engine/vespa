// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * @author mpolden
 */
public record NodeVersion(HostName hostname,
                          ZoneId zone,
                          Version currentVersion,
                          Version wantedVersion,
                          Optional<Instant> suspendedAt) {

    public NodeVersion {
        Objects.requireNonNull(hostname, "hostname must be non-null");
        Objects.requireNonNull(zone, "zone must be non-null");
        Objects.requireNonNull(currentVersion, "version must be non-null");
        Objects.requireNonNull(wantedVersion, "wantedVersion must be non-null");
        Objects.requireNonNull(suspendedAt, "suspendedAt must be non-null");
    }

    /** Returns the duration of the change in this, measured relative to instant */
    public Duration changeDuration(Instant instant) {
        if (!upgrading()) return Duration.ZERO;
        if (suspendedAt.isEmpty()) return Duration.ZERO; // Node hasn't suspended to apply the change yet
        return Duration.between(suspendedAt.get(), instant).abs();
    }

    @Override
    public String toString() {
        return hostname + ": " + currentVersion.toFullString() + " -> " + wantedVersion.toFullString() +
               " [zone=" + zone + ", suspendedAt=" + suspendedAt.map(Instant::toString)
                                                                .orElse("<not suspended>") + "]";
    }

    /** Returns whether this is upgrading */
    private boolean upgrading() {
        return currentVersion.isBefore(wantedVersion);
    }

}
