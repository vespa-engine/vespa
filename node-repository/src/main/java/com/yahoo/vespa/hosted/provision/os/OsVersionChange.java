// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.google.common.collect.ImmutableSortedMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;

import java.util.Map;
import java.util.Objects;

/**
 * The OS version change being deployed in a {@link com.yahoo.vespa.hosted.provision.NodeRepository}.
 *
 * @author mpolden
 */
public class OsVersionChange {

    public static final OsVersionChange NONE = new OsVersionChange(Map.of());

    private final Map<NodeType, Version> targets;

    public OsVersionChange(Map<NodeType, Version> targets) {
        this.targets = ImmutableSortedMap.copyOf(Objects.requireNonNull(targets));
    }

    /** Version targets for this */
    public Map<NodeType, Version> targets() {
        return targets;
    }

    /** Returns a copy of this with target versions set to given value */
    public OsVersionChange with(Map<NodeType, Version> targets) {
        return new OsVersionChange(targets);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsVersionChange that = (OsVersionChange) o;
        return targets.equals(that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targets);
    }

}
