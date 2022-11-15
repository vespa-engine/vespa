// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * The OS version target for a cloud and the time it was scheduled.
 *
 * @author mpolden
 */
public record OsVersionTarget(OsVersion osVersion, Instant scheduledAt) implements VersionTarget, Comparable<OsVersionTarget> {

    public OsVersionTarget {
        Objects.requireNonNull(osVersion);
        Objects.requireNonNull(scheduledAt);
    }

    @Override
    public int compareTo(OsVersionTarget o) {
        return osVersion.compareTo(o.osVersion);
    }

    @Override
    public Version version() {
        return osVersion.version();
    }

    @Override
    public boolean downgrade() {
        return false; // Not supported by this target type
    }

}
