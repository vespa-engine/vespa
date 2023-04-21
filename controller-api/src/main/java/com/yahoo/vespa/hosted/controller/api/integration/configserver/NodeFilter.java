// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;

import java.util.Objects;
import java.util.Set;

/**
 * A filter for listing nodes.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class NodeFilter {

    private final boolean includeDeprovisioned;
    private final Set<Node.State> states;
    private final Set<HostName> hostnames;
    private final Set<HostName> parentHostnames;
    private final Set<ApplicationId> applications;
    private final Set<ClusterSpec.Id> clusterIds;
    private final Set<Node.ClusterType> clusterTypes;

    private NodeFilter(boolean includeDeprovisioned, Set<Node.State> states, Set<HostName> hostnames,
                       Set<HostName> parentHostnames, Set<ApplicationId> applications,
                       Set<ClusterSpec.Id> clusterIds, Set<Node.ClusterType> clusterTypes) {
        this.includeDeprovisioned = includeDeprovisioned;
        // Uses Guava Set to preserve insertion order
        this.states = ImmutableSet.copyOf(Objects.requireNonNull(states));
        this.hostnames = ImmutableSet.copyOf(Objects.requireNonNull(hostnames));
        this.parentHostnames = ImmutableSet.copyOf(Objects.requireNonNull(parentHostnames));
        this.applications = ImmutableSet.copyOf(Objects.requireNonNull(applications));
        this.clusterIds = ImmutableSet.copyOf(Objects.requireNonNull(clusterIds));
        this.clusterTypes = ImmutableSet.copyOf(Objects.requireNonNull(clusterTypes));
        if (!includeDeprovisioned && states.contains(Node.State.deprovisioned)) {
            throw new IllegalArgumentException("Must include deprovisioned nodes when matching deprovisioned state");
        }
    }

    public boolean includeDeprovisioned() {
        return includeDeprovisioned;
    }

    public Set<Node.State> states() {
        return states;
    }

    public Set<HostName> hostnames() {
        return hostnames;
    }

    public Set<HostName> parentHostnames() {
        return parentHostnames;
    }

    public Set<ApplicationId> applications() {
        return applications;
    }

    public Set<ClusterSpec.Id> clusterIds() {
        return clusterIds;
    }

    public Set<Node.ClusterType> clusterTypes() {
        return clusterTypes;
    }

    public NodeFilter includeDeprovisioned(boolean includeDeprovisioned) {
        return new NodeFilter(includeDeprovisioned, states, hostnames, parentHostnames, applications, clusterIds, clusterTypes);
    }

    public NodeFilter states(Node.State... states) {
        return states(ImmutableSet.copyOf(states));
    }

    public NodeFilter states(Set<Node.State> states) {
        return new NodeFilter(includeDeprovisioned, states, hostnames, parentHostnames, applications, clusterIds, clusterTypes);
    }

    public NodeFilter hostnames(HostName... hostnames) {
        return hostnames(ImmutableSet.copyOf(hostnames));
    }

    public NodeFilter hostnames(Set<HostName> hostnames) {
        return new NodeFilter(includeDeprovisioned, states, hostnames, parentHostnames, applications, clusterIds, clusterTypes);
    }

    public NodeFilter parentHostnames(HostName... parentHostnames) {
        return parentHostnames(ImmutableSet.copyOf(parentHostnames));
    }

    public NodeFilter parentHostnames(Set<HostName> parentHostnames) {
        return new NodeFilter(includeDeprovisioned, states, hostnames, parentHostnames, applications, clusterIds, clusterTypes);
    }

    public NodeFilter applications(ApplicationId... applications) {
        return applications(ImmutableSet.copyOf(applications));
    }

    public NodeFilter applications(Set<ApplicationId> applications) {
        return new NodeFilter(includeDeprovisioned, states, hostnames, parentHostnames, applications, clusterIds, clusterTypes);
    }

    public NodeFilter clusterIds(ClusterSpec.Id... clusterIds) {
        return clusterIds(ImmutableSet.copyOf(clusterIds));
    }

    public NodeFilter clusterIds(Set<ClusterSpec.Id> clusterIds) {
        return new NodeFilter(includeDeprovisioned, states, hostnames, parentHostnames, applications, clusterIds, clusterTypes);
    }

    public NodeFilter clusterTypes(Node.ClusterType... clusterTypes) {
        return clusterTypes(ImmutableSet.copyOf(clusterTypes));
    }

    public NodeFilter clusterTypes(Set<Node.ClusterType> clusterTypes) {
        return new NodeFilter(includeDeprovisioned, states, hostnames, parentHostnames, applications, clusterIds, clusterTypes);
    }

    /** A filter which matches all nodes, except deprovisioned ones */
    public static NodeFilter all() {
        return new NodeFilter(false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

}
