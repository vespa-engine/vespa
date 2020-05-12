// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * An {@link OsVersion} and its upgrade budget.
 *
 * @author mpolden
 */
public class OsVersionTarget implements Comparable<OsVersionTarget> {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private final OsVersion osVersion;
    private final Optional<Duration> upgradeBudget;

    public OsVersionTarget(OsVersion osVersion, Optional<Duration> upgradeBudget) {
        this.osVersion = Objects.requireNonNull(osVersion);
        this.upgradeBudget = requireNotNegative(upgradeBudget);
    }

    /** The OS version contained in this target */
    public OsVersion osVersion() {
        return osVersion;
    }

    /** The total time budget across all zones for applying target, if any */
    public Optional<Duration> upgradeBudget() {
        return upgradeBudget;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsVersionTarget that = (OsVersionTarget) o;
        return osVersion.equals(that.osVersion) &&
               upgradeBudget.equals(that.upgradeBudget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(osVersion, upgradeBudget);
    }

    private static Optional<Duration> requireNotNegative(Optional<Duration> duration) {
        Objects.requireNonNull(duration);
        if (duration.isEmpty()) return duration;
        if (duration.get().isNegative()) throw new IllegalArgumentException("Duration cannot be negative");
        return duration;
    }

    @Override
    public int compareTo(@NotNull OsVersionTarget o) {
        return osVersion.compareTo(o.osVersion);
    }

}
