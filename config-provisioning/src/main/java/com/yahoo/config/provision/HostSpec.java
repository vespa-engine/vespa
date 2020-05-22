// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

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
    private final List<String> aliases;

    private final NodeResources realResources;

    private final NodeResources advertisedResources;

    /** The current membership role of this host in the cluster it belongs to */
    private final Optional<ClusterMembership> membership;

    private final Optional<Version> version;

    private final Optional<DockerImage> dockerImageRepo;

    private final Optional<NetworkPorts> networkPorts;

    private final Optional<NodeResources> requestedResources;

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, Optional<ClusterMembership> membership) {
        this(hostname, new ArrayList<>(),
             NodeResources.unspecified(), NodeResources.unspecified(),
             membership,
             Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, ClusterMembership membership, Flavor flavor, Optional<Version> version) {
        this(hostname, new ArrayList<>(),
             flavor.resources(), flavor.resources(),
             Optional.of(membership), version, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Create a host in a non-cloud system, where hosts are specified in config */
    public HostSpec(String hostname, List<String> aliases) {
        this(hostname, aliases,
             NodeResources.unspecified(), NodeResources.unspecified(),
             Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, List<String> aliases, Flavor flavor) {
        this(hostname, aliases,
             flavor.resources(), flavor.resources(),
             Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, List<String> aliases, ClusterMembership membership) {
        this(hostname, aliases,
             NodeResources.unspecified(), NodeResources.unspecified(),
             Optional.of(membership),
             Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, List<String> aliases, Optional<Flavor> flavor, Optional<ClusterMembership> membership) {
        this(hostname, aliases,
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             membership, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, List<String> aliases, Optional<Flavor> flavor,
                    Optional<ClusterMembership> membership, Optional<Version> version) {
        this(hostname, aliases,
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             membership, version,
             Optional.empty(), Optional.empty(), Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, List<String> aliases, Optional<Flavor> flavor,
                    Optional<ClusterMembership> membership, Optional<Version> version,
                    Optional<NetworkPorts> networkPorts) {
        this(hostname, aliases,
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             membership, version, networkPorts,
             Optional.empty(),
             Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, List<String> aliases,
                    Optional<Flavor> flavor,
                    Optional<ClusterMembership> membership, Optional<Version> version,
                    Optional<NetworkPorts> networkPorts, Optional<NodeResources> requestedResources) {
        this(hostname, aliases,
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             membership, version, networkPorts, requestedResources, Optional.empty());
    }

    // TODO: Remove after June 2020
    @Deprecated
    public HostSpec(String hostname, List<String> aliases, Optional<Flavor> flavor,
                    Optional<ClusterMembership> membership, Optional<Version> version,
                    Optional<NetworkPorts> networkPorts, Optional<NodeResources> requestedResources,
                    Optional<DockerImage> dockerImageRepo) {
        this(hostname, aliases,
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             flavor.map(f -> f.resources()).orElse(NodeResources.unspecified()),
             membership, version, networkPorts, requestedResources, dockerImageRepo);
    }

    /** Create a host in a hosted system */
    public HostSpec(String hostname,
                    NodeResources realResources,
                    NodeResources advertisedResurces,
                    NodeResources requestedResources,
                    ClusterMembership membership,
                    Optional<Version> version,
                    Optional<NetworkPorts> networkPorts,
                    Optional<DockerImage> dockerImageRepo) {
        this(hostname, List.of(),
             realResources, advertisedResurces,
             Optional.of(membership),
             version, networkPorts, requestedResources.asOptional(), dockerImageRepo);
    }

    /** Create a fully specified host for any system */
    public HostSpec(String hostname, List<String> aliases,
                    NodeResources realResources, NodeResources advertisedResurces,
                    Optional<ClusterMembership> membership, Optional<Version> version,
                    Optional<NetworkPorts> networkPorts, Optional<NodeResources> requestedResources,
                    Optional<DockerImage> dockerImageRepo) {
        if (hostname == null || hostname.isEmpty()) throw new IllegalArgumentException("Hostname must be specified");
        this.hostname = hostname;
        this.aliases = List.copyOf(aliases);
        this.realResources = realResources;
        this.advertisedResources = advertisedResurces;
        this.membership = membership;
        this.version = Objects.requireNonNull(version, "Version cannot be null but can be empty");
        this.networkPorts = Objects.requireNonNull(networkPorts, "Network ports cannot be null but can be empty");
        this.requestedResources = Objects.requireNonNull(requestedResources, "RequestedResources cannot be null");
        this.dockerImageRepo = Objects.requireNonNull(dockerImageRepo, "Docker image repo cannot be null but can be empty");
    }

    /** Returns the name identifying this host */
    public String hostname() { return hostname; }

    /** Returns the aliases of this host as an immutable list. This may be empty but never null. */
    public List<String> aliases() { return aliases; }

    /** The real resources available for Vespa processes on this node, after subtracting infrastructure overhead. */
    public NodeResources realResources() { return realResources; }

    /** The total advertised resources of this node, typically matching what's requested. */
    public NodeResources advertisedResources() { return advertisedResources; }

    /** A flavor contained the advertised resources of this host */
    // TODO: Remove after June 2020
    public Optional<Flavor> flavor() {
        return advertisedResources.asOptional().map(resources -> new Flavor(resources));
    }

    /** Returns the current version of Vespa running on this node, or empty if not known */
    public Optional<com.yahoo.component.Version> version() { return version; }

    /** Returns the membership of this host, or an empty value if not present */
    public Optional<ClusterMembership> membership() { return membership; }

    /** Returns the network port allocations on this host, or empty if not present */
    public Optional<NetworkPorts> networkPorts() { return networkPorts; }

    /** Returns the requested resources leading to this host being provisioned, or empty if unspecified */
    public Optional<NodeResources> requestedResources() { return requestedResources; }

    public Optional<DockerImage> dockerImageRepo() { return dockerImageRepo; }

    public HostSpec withPorts(Optional<NetworkPorts> ports) {
        return new HostSpec(hostname, aliases, realResources, advertisedResources, membership, version, ports, requestedResources, dockerImageRepo);
    }

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
