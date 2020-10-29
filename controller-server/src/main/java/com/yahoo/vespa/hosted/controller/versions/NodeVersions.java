// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A filterable list of {@link NodeVersion}s. This is immutable.
 *
 * @author mpolden
 */
public class NodeVersions {

    private final ImmutableMap<HostName, NodeVersion> nodeVersions;

    public NodeVersions(Map<HostName, NodeVersion> nodeVersions) {
        this.nodeVersions = ImmutableMap.copyOf(Objects.requireNonNull(nodeVersions));
    }

    public Map<HostName, NodeVersion> asMap() {
        return nodeVersions;
    }

    /** Returns host names in this */
    public Set<HostName> hostnames() {
        return nodeVersions.keySet();
    }

    /** Returns a copy of this containing only node versions of given version */
    public NodeVersions matching(Version version) {
        return copyOf(nodeVersions.values(), nodeVersion -> nodeVersion.currentVersion().equals(version));
    }

    /** Returns number of node versions in this */
    public int size() {
        return nodeVersions.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeVersions that = (NodeVersions) o;
        return nodeVersions.equals(that.nodeVersions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeVersions);
    }

    public static NodeVersions copyOf(List<NodeVersion> nodeVersions) {
        return copyOf(nodeVersions, (ignored) -> true);
    }

    public static NodeVersions copyOf(Map<HostName, NodeVersion> nodeVersions) {
        return new NodeVersions(nodeVersions);
    }

    private static NodeVersions copyOf(Collection<NodeVersion> nodeVersions, Predicate<NodeVersion> predicate) {
        var newNodeVersions = ImmutableMap.<HostName, NodeVersion>builder();
        for (var nodeVersion : nodeVersions) {
            if (!predicate.test(nodeVersion)) continue;
            newNodeVersions.put(nodeVersion.hostname(), nodeVersion);
        }
        return new NodeVersions(newNodeVersions.build());
    }

}
