// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The target OS version for a {@link NodeType}.
 *
 * @author mpolden
 */
public class OsVersionTarget {

    private final NodeType nodeType;
    private final Version version;
    private final Optional<Duration> upgradeBudget;
    private final Optional<Instant> lastRetiredAt;

    public OsVersionTarget(NodeType nodeType, Version version, Optional<Duration> upgradeBudget, Optional<Instant> lastRetiredAt) {
        this.nodeType = Objects.requireNonNull(nodeType);
        this.version = Objects.requireNonNull(version);
        this.upgradeBudget = requireNotNegative(upgradeBudget);
        this.lastRetiredAt = Objects.requireNonNull(lastRetiredAt);
    }

    /** The node type this applies to */
    public NodeType nodeType() {
        return nodeType;
    }

    /** The OS version of this target */
    public Version version() {
        return version;
    }

    /** The upgrade budget for this. All nodes targeting this must upgrade within this budget */
    public Optional<Duration> upgradeBudget() {
        return upgradeBudget;
    }

    /** The most recent time a node was retired to apply a version upgrade */
    public Optional<Instant> lastRetiredAt() {
        return lastRetiredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsVersionTarget target = (OsVersionTarget) o;
        return nodeType == target.nodeType &&
               version.equals(target.version) &&
               upgradeBudget.equals(target.upgradeBudget) &&
               lastRetiredAt.equals(target.lastRetiredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeType, version, upgradeBudget, lastRetiredAt);
    }

    private static Optional<Duration> requireNotNegative(Optional<Duration> duration) {
        Objects.requireNonNull(duration);
        if (duration.isEmpty()) return duration;
        if (duration.get().isNegative()) throw new IllegalArgumentException("Duration cannot be negative");
        return duration;
    }

}
