// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.google.common.collect.ImmutableSortedMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The OS version change being deployed in a {@link com.yahoo.vespa.hosted.provision.NodeRepository}.
 *
 * @author mpolden
 */
public class OsVersionChange {

    public static final OsVersionChange NONE = new OsVersionChange(Map.of());

    private final Map<NodeType, OsVersionTarget> targets;

    public OsVersionChange(Map<NodeType, OsVersionTarget> targets) {
        this.targets = ImmutableSortedMap.copyOf(Objects.requireNonNull(targets));
    }

    /** Version targets in this */
    public Map<NodeType, OsVersionTarget> targets() {
        return targets;
    }

    /** Returns a copy of this with target for given node type removed */
    public OsVersionChange withoutTarget(NodeType nodeType) {
        var targets = new HashMap<>(this.targets);
        targets.remove(nodeType);
        return new OsVersionChange(targets);
    }

    /** Returns a copy of this with given target added */
    public OsVersionChange withTarget(Version version, NodeType nodeType) {
        var copy = new HashMap<>(this.targets);
        copy.compute(nodeType, (key, prevTarget) -> new OsVersionTarget(nodeType, version));
        return new OsVersionChange(copy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsVersionChange change = (OsVersionChange) o;
        return targets.equals(change.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targets);
    }

    private void requireTarget(NodeType nodeType) {
        if (!targets.containsKey(nodeType)) throw new IllegalArgumentException("No target set for " + nodeType);
    }

}
