// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The OS version change being deployed in a {@link com.yahoo.vespa.hosted.provision.NodeRepository}.
 *
 * @author mpolden
 */
public record OsVersionChange(SortedMap<NodeType, OsVersionTarget> targets) {

    public static final OsVersionChange NONE = new OsVersionChange(new TreeMap<>());

    public OsVersionChange(SortedMap<NodeType, OsVersionTarget> targets) {
        this.targets = Collections.unmodifiableSortedMap(new TreeMap<>(targets));
    }

    /** Returns a copy of this with target for given node type removed */
    public OsVersionChange withoutTarget(NodeType nodeType) {
        var targets = new TreeMap<>(this.targets);
        targets.remove(nodeType);
        return new OsVersionChange(targets);
    }

    /** Returns a copy of this with given target added */
    public OsVersionChange withTarget(Version version, NodeType nodeType) {
        var copy = new TreeMap<>(this.targets);
        copy.compute(nodeType, (key, prevTarget) -> new OsVersionTarget(nodeType, version));
        return new OsVersionChange(copy);
    }

}
