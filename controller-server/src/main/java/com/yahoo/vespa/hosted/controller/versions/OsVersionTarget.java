// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The OS version target for a cloud/system, containing the {@link OsVersion} and its upgrade budget.
 *
 * @author mpolden
 */
public record OsVersionTarget(OsVersion osVersion,
                              Duration upgradeBudget,
                              Instant scheduledAt) implements VersionTarget, Comparable<OsVersionTarget> {

    public OsVersionTarget {
        Objects.requireNonNull(osVersion);
        Objects.requireNonNull(upgradeBudget);
        Objects.requireNonNull(scheduledAt);
        if (upgradeBudget.isNegative()) throw new IllegalArgumentException("upgradeBudget cannot be negative");
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
