// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

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

    private final NodeResources realResources;
    private final NodeResources advertisedResources;
    private final NodeResources requestedResources;

    /** The current membership role of this host in the cluster it belongs to */
    private final Optional<ClusterMembership> membership;

    private final Optional<Version> version;

    private final Optional<DockerImage> dockerImageRepo;

    private final Optional<NetworkPorts> networkPorts;

    /** Create a host in a non-cloud system, where hosts are specified in config */
    public HostSpec(String hostname, Optional<NetworkPorts> networkPorts) {
        this(hostname,
             NodeResources.unspecified(), NodeResources.unspecified(), NodeResources.unspecified(),
             Optional.empty(), Optional.empty(), networkPorts, Optional.empty());
    }

    // TODO: Remove usage
    public HostSpec(String hostname, List<String> ignored, Optional<NetworkPorts> networkPorts) {
        this(hostname,
             NodeResources.unspecified(), NodeResources.unspecified(), NodeResources.unspecified(),
             Optional.empty(), Optional.empty(), networkPorts, Optional.empty());
    }

    /** Create a host in a hosted system */
    public HostSpec(String hostname,
                    NodeResources realResources,
                    NodeResources advertisedResources,
                    NodeResources requestedResources,
                    ClusterMembership membership,
                    Optional<Version> version,
                    Optional<NetworkPorts> networkPorts,
                    Optional<DockerImage> dockerImageRepo) {
        this(hostname,
             realResources,
             advertisedResources,
             requestedResources,
             Optional.of(membership),
             version,
             networkPorts,
             dockerImageRepo);
    }

    private HostSpec(String hostname,
                     NodeResources realResources,
                     NodeResources advertisedResurces,
                     NodeResources requestedResources,
                     Optional<ClusterMembership> membership,
                     Optional<Version> version,
                     Optional<NetworkPorts> networkPorts,
                     Optional<DockerImage> dockerImageRepo) {
        if (hostname == null || hostname.isEmpty()) throw new IllegalArgumentException("Hostname must be specified");
        this.hostname = hostname;
        this.realResources = Objects.requireNonNull(realResources);
        this.advertisedResources = Objects.requireNonNull(advertisedResurces);
        this.requestedResources = Objects.requireNonNull(requestedResources, "RequestedResources cannot be null");
        this.membership = Objects.requireNonNull(membership);
        this.version = Objects.requireNonNull(version, "Version cannot be null but can be empty");
        this.networkPorts = Objects.requireNonNull(networkPorts, "Network ports cannot be null but can be empty");
        this.dockerImageRepo = Objects.requireNonNull(dockerImageRepo, "Docker image repo cannot be null but can be empty");
    }

    /** Returns the name identifying this host */
    public String hostname() { return hostname; }

    /** The real resources available for Vespa processes on this node, after subtracting infrastructure overhead. */
    public NodeResources realResources() { return realResources; }

    /** The total advertised resources of this node, typically matching what's requested. */
    public NodeResources advertisedResources() { return advertisedResources; }

    /** Returns the current version of Vespa running on this node, or empty if not known */
    public Optional<com.yahoo.component.Version> version() { return version; }

    /** Returns the membership of this host, or an empty value if not present */
    public Optional<ClusterMembership> membership() { return membership; }

    /** Returns the network port allocations on this host, or empty if not present */
    public Optional<NetworkPorts> networkPorts() { return networkPorts; }

    /** Returns the requested resources leading to this host being provisioned, or empty if unspecified */
    public Optional<NodeResources> requestedResources() { return requestedResources.asOptional(); }

    public Optional<DockerImage> dockerImageRepo() { return dockerImageRepo; }

    public HostSpec withPorts(Optional<NetworkPorts> ports) {
        return new HostSpec(hostname, realResources, advertisedResources, requestedResources, membership, version, ports, dockerImageRepo);
    }

    @Override
    public String toString() {
        return hostname +
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
