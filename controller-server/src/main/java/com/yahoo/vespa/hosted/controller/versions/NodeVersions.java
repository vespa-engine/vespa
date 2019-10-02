// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

/**
 * A filterable list of {@link NodeVersion}s. This is immutable.
 *
 * @author mpolden
 */
public class NodeVersions {

    public static final NodeVersions EMPTY = new NodeVersions(Map.of());

    private final Map<HostName, NodeVersion> nodeVersions;

    public NodeVersions(Map<HostName, NodeVersion> nodeVersions) {
        this.nodeVersions = ImmutableSortedMap.copyOf(Objects.requireNonNull(nodeVersions));
    }

    public Map<HostName, NodeVersion> asMap() {
        return nodeVersions;
    }

    /** Returns host names in this, grouped by version */
    public ListMultimap<Version, HostName> asVersionMap() {
        var versions = ImmutableListMultimap.<Version, HostName>builder();
        for (var kv : nodeVersions.entrySet()) {
            versions.put(kv.getValue().version(), kv.getKey());
        }
        return versions.build();
    }

    /** Returns host names in this */
    public Set<HostName> hostnames() {
        return nodeVersions.keySet();
    }

    /** Returns a copy of this containing only node versions of given version */
    public NodeVersions matching(Version version) {
        return filter(nodeVersion -> nodeVersion.version().equals(version));
    }

    /** Returns a copy of this containing only node versions that last changed before given instant */
    public NodeVersions changedBefore(Instant instant) {
        return filter(nodeVersion -> nodeVersion.changedAt().isBefore(instant));
    }

    /** Returns a copy of this retaining only node versions for the given host names */
    public NodeVersions retainAll(Set<HostName> hostnames) {
        if (this.nodeVersions.keySet().equals(hostnames)) return this;

        var nodeVersions = new LinkedHashMap<>(this.nodeVersions);
        nodeVersions.keySet().retainAll(hostnames);
        return new NodeVersions(nodeVersions);
    }

    /** Returns number of node versions in this */
    public int size() {
        return nodeVersions.size();
    }

    /** Returns a copy of this with a new node version added. Duplicate node versions are ignored */
    public NodeVersions with(NodeVersion nodeVersion) {
        var existing = nodeVersions.get(nodeVersion.hostname());
        if (existing != null && existing.version().equals(nodeVersion.version())) return this;

        var nodeVersions = new LinkedHashMap<>(this.nodeVersions);
        nodeVersions.put(nodeVersion.hostname(), nodeVersion);
        return new NodeVersions(nodeVersions);
    }

    private NodeVersions filter(Predicate<NodeVersion> predicate) {
        return nodeVersions.entrySet().stream()
                           .filter(kv -> predicate.test(kv.getValue()))
                           .collect(collectingAndThen(toMap(Map.Entry::getKey, Map.Entry::getValue),
                                                      NodeVersions::new));
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
