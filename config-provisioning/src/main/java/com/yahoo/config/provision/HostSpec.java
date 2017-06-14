// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A specification of a host and its role.
 * The identity of a host is determined by its name.
 *
 * @author hmusum
 */
public class HostSpec implements Comparable<HostSpec> {

    /** The name of this host */
    private final String hostname;

    /** Aliases of this host */
    private final List<String> aliases;

    /** The current membership role of this host in the cluster it belongs to */
    private final Optional<ClusterMembership> membership;

    private final Optional<Flavor> flavor;

    public HostSpec(String hostname, Optional<ClusterMembership> membership) {
        this(hostname, new ArrayList<>(), Optional.empty(), membership);
    }

    public HostSpec(String hostname, ClusterMembership membership, Flavor flavor) {
        this(hostname, new ArrayList<>(), Optional.of(flavor), Optional.of(membership));
    }

    public HostSpec(String hostname, List<String> aliases) {
        this(hostname, aliases, Optional.empty(), Optional.empty());
    }

    public HostSpec(String hostname, List<String> aliases, ClusterMembership membership) {
        this(hostname, aliases, Optional.empty(), Optional.of(membership));
    }

    public HostSpec(String hostname, List<String> aliases, Optional<Flavor> flavor, Optional<ClusterMembership> membership) {
        if (hostname == null || hostname.isEmpty()) throw new IllegalArgumentException("Hostname must be specified");
        this.hostname = hostname;
        this.aliases = ImmutableList.copyOf(aliases);
        this.flavor = flavor;
        this.membership = membership;
    }

    /** Returns the name identifying this host */
    public String hostname() { return hostname; }

    /** Returns the aliases of this host as an immutable list. This may be empty but never null. */
    public List<String> aliases() { return aliases; }

    public Optional<Flavor> flavor() { return flavor; }

    /** Returns the membership of this host, or an empty value if not present */
    public Optional<ClusterMembership> membership() { return membership; }

    @Override
    public String toString() {
        return hostname +
               (! aliases.isEmpty() ? " (aliases: " + aliases + ")" : "") +
               (membership.isPresent() ? " (membership: " + membership.get() + ")" : " (no membership)");
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof HostSpec)) return false;
        HostSpec other = (HostSpec) o;
        return this.hostname().equals(other.hostname());
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
