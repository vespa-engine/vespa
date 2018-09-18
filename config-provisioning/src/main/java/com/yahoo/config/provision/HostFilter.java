// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.collect.ImmutableSet;
import com.yahoo.text.Text;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A filter which matches a host depending on its properties.
 *
 * @author bratseth
 */
public class HostFilter {

    // Filters. Empty to not filter on this property
    private final Set<String> hostnames;
    private final Set<String> flavors;
    private final Set<ClusterSpec.Type> clusterTypes;
    private final Set<ClusterSpec.Id> clusterIds;

    private HostFilter(Set<String> hostnames,
                       Set<String> flavors,
                       Set<ClusterSpec.Type> clusterTypes,
                       Set<ClusterSpec.Id> clusterIds) {
        Objects.requireNonNull(hostnames, "Hostnames cannot be null, use an empty list");
        Objects.requireNonNull(flavors, "Flavors cannot be null, use an empty list");
        Objects.requireNonNull(clusterTypes, "clusterTypes cannot be null, use an empty list");
        Objects.requireNonNull(clusterIds, "clusterIds cannot be null, use an empty list");

        this.hostnames = hostnames;
        this.flavors = flavors;
        this.clusterTypes = clusterTypes;
        this.clusterIds = clusterIds;
    }

    /** Returns true if this filter matches the given host properties */
    public boolean matches(String hostname, String flavor, Optional<ClusterMembership> membership) {
        if ( ! hostnames.isEmpty() && ! hostnames.contains(hostname)) return false;
        if ( ! flavors.isEmpty() && ! flavors.contains(flavor)) return false;
        if ( ! clusterTypes.isEmpty() && ! (membership.isPresent() && clusterTypes.contains(membership.get().cluster().type()))) return false;
        if ( ! clusterIds.isEmpty() && ! (membership.isPresent() && clusterIds.contains(membership.get().cluster().id()))) return false;
        return true;
    }

    /** Returns a filter which matches all hosts */
    public static HostFilter all() {
        return new HostFilter(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    /** Returns a filter which matches a given host only */
    public static HostFilter hostname(String hostname) {
        return new HostFilter(Collections.singleton(hostname), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    /** Returns a filter which matches a given flavor only */
    public static HostFilter flavor(String flavor) {
        return new HostFilter(Collections.emptySet(), Collections.singleton(flavor), Collections.emptySet(), Collections.emptySet());
    }

    /** Returns a filter which matches a given cluster type only */
    public static HostFilter clusterType(ClusterSpec.Type clusterType) {
        return new HostFilter(Collections.emptySet(), Collections.emptySet(), Collections.singleton(clusterType), Collections.emptySet());
    }

    /** Returns a filter which matches a given cluster id only */
    public static HostFilter clusterId(ClusterSpec.Id clusterId) {
        return new HostFilter(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.singleton(clusterId));
    }

    /** Returns a host filter from three optional conditions */
    public static HostFilter from(Collection<String> hostNames,
                                  Collection<String> flavors,
                                  Collection<ClusterSpec.Type> clusterTypes,
                                  Collection<ClusterSpec.Id> clusterIds) {
        return new HostFilter(ImmutableSet.copyOf(hostNames),
                              ImmutableSet.copyOf(flavors),
                              ImmutableSet.copyOf(clusterTypes),
                              ImmutableSet.copyOf(clusterIds));
    }

    /** Returns a host filter from three comma and-or space separated string lists. The strings may be null or empty. */
    public static HostFilter from(String hostNames, String flavors, String clusterTypes, String clusterIds) {
        return new HostFilter(
                Text.split(hostNames),
                Text.split(flavors),
                Text.split(clusterTypes).stream().map(ClusterSpec.Type::from).collect(Collectors.toSet()),
                Text.split(clusterIds).stream().map(ClusterSpec.Id::from).collect(Collectors.toSet()));
    }

}
