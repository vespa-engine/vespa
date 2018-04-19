// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A specification of a host and its role.
 * Equality and order is determined by the host name.
 *
 * @author hmusum
 */
public class HostSpec implements Comparable<HostSpec> {

    /** The name of this host */
    private final String hostname;

    /** Aliases of this host */
    private final ImmutableList<String> aliases;

    /** The current membership role of this host in the cluster it belongs to */
    private final Optional<ClusterMembership> membership;

    private final Optional<Flavor> flavor;

    private final Optional<com.yahoo.component.Version> version;

    public HostSpec(String hostname, Optional<ClusterMembership> membership) {
        this(hostname, new ArrayList<>(), Optional.empty(), membership);
    }

    // TODO: Remove after May 2018
    public HostSpec(String hostname, ClusterMembership membership, Flavor flavor) {
        this(hostname, new ArrayList<>(), Optional.of(flavor), Optional.of(membership));
    }

    public HostSpec(String hostname, ClusterMembership membership, Flavor flavor, Optional<com.yahoo.component.Version> version) {
        this(hostname, new ArrayList<>(), Optional.of(flavor), Optional.of(membership), version);
    }

    public HostSpec(String hostname, List<String> aliases) {
        this(hostname, aliases, Optional.empty(), Optional.empty());
    }

    public HostSpec(String hostname, List<String> aliases, ClusterMembership membership) {
        this(hostname, aliases, Optional.empty(), Optional.of(membership));
    }

    public HostSpec(String hostname, List<String> aliases, Optional<Flavor> flavor, Optional<ClusterMembership> membership) {
        this(hostname, aliases, flavor, membership, Optional.empty());
    }

    public HostSpec(String hostname, List<String> aliases, Optional<Flavor> flavor,
                    Optional<ClusterMembership> membership, Optional<com.yahoo.component.Version> version) {
        if (hostname == null || hostname.isEmpty()) throw new IllegalArgumentException("Hostname must be specified");
        Objects.requireNonNull(version, "Version cannot be null but can be empty");
        this.hostname = hostname;
        this.aliases = ImmutableList.copyOf(aliases);
        this.flavor = flavor;
        this.membership = membership;
        this.version = version;
    }

    /** Returns the name identifying this host */
    public String hostname() { return hostname; }

    /** Returns the aliases of this host as an immutable list. This may be empty but never null. */
    public List<String> aliases() { return aliases; }

    public Optional<Flavor> flavor() { return flavor; }

    /** Returns the current version of Vespa running on this node, or empty if not known */
    public Optional<com.yahoo.component.Version> version() { return version; }

    /** Returns the membership of this host, or an empty value if not present */
    public Optional<ClusterMembership> membership() { return membership; }

    @Override
    public String toString() {
        return hostname +
               (! aliases.isEmpty() ? " (aliases: " + aliases + ")" : "") +
               (membership.isPresent() ? " (membership: " + membership.get() + ")" : " (no membership)");
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof HostSpec)) return false;

        return ((HostSpec)other).hostname.equals(this.hostname);
    }

    @Override
    public int hashCode() {
        return hostname.hashCode();
    }

    @Override
    public int compareTo(HostSpec other) {
        return hostname.compareTo(other.hostname);
    }

}
