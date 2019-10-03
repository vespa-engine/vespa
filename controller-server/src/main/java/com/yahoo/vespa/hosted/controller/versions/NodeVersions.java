// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;

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

    public static final NodeVersions EMPTY = new NodeVersions(ImmutableMap.of());

    private final ImmutableMap<HostName, NodeVersion> nodeVersions;

    public NodeVersions(ImmutableMap<HostName, NodeVersion> nodeVersions) {
        this.nodeVersions = Objects.requireNonNull(nodeVersions);
    }

    public Map<HostName, NodeVersion> asMap() {
        return nodeVersions;
    }

    /** Returns host names in this, grouped by version */
    public ListMultimap<Version, HostName> asVersionMap() {
        var versions = ImmutableListMultimap.<Version, HostName>builder();
        for (var kv : nodeVersions.entrySet()) {
            versions.put(kv.getValue().currentVersion(), kv.getKey());
        }
        return versions.build();
    }

    /** Returns host names in this */
    public Set<HostName> hostnames() {
        return nodeVersions.keySet();
    }

    /** Returns a copy of this containing only node versions of given version */
    public NodeVersions matching(Version version) {
        return filter(nodeVersion -> nodeVersion.currentVersion().equals(version));
    }

    /** Returns number of node versions in this */
    public int size() {
        return nodeVersions.size();
    }

    /** Returns a copy of this containing only the given node versions */
    public NodeVersions with(List<NodeVersion> nodeVersions) {
        var newNodeVersions = ImmutableMap.<HostName, NodeVersion>builder();
        for (var nodeVersion : nodeVersions) {
            var existing = this.nodeVersions.get(nodeVersion.hostname());
            if (existing != null) {
                newNodeVersions.put(nodeVersion.hostname(), existing.withCurrentVersion(nodeVersion.currentVersion(),
                                                                                        nodeVersion.changedAt())
                                                                    .withWantedVersion(nodeVersion.wantedVersion()));
            } else {
                newNodeVersions.put(nodeVersion.hostname(), nodeVersion);
            }
        }
        return new NodeVersions(newNodeVersions.build());
    }

    private NodeVersions filter(Predicate<NodeVersion> predicate) {
        var newNodeVersions = ImmutableMap.<HostName, NodeVersion>builder();
        for (var kv : nodeVersions.entrySet()) {
            if (!predicate.test(kv.getValue())) continue;
            newNodeVersions.put(kv.getKey(), kv.getValue());
        }
        return new NodeVersions(newNodeVersions.build());
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

}
