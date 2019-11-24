// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;

import java.util.Objects;
import java.util.Optional;

/**
 * A node in hosted Vespa.
 *
 * @author mpolden
 * @author jonmv
 */
public class Node {

    private final HostName hostname;
    private final Optional<HostName> parentHostname;
    private final State state;
    private final NodeType type;
    private final NodeResources resources;
    private final Optional<ApplicationId> owner;
    private final Version currentVersion;
    private final Version wantedVersion;
    private final Version currentOsVersion;
    private final Version wantedOsVersion;
    private final ServiceState serviceState;
    private final long restartGeneration;
    private final long wantedRestartGeneration;
    private final long rebootGeneration;
    private final long wantedRebootGeneration;
    private final int cost;
    private final String canonicalFlavor;
    private final String clusterId;
    private final ClusterType clusterType;

    public Node(HostName hostname, Optional<HostName> parentHostname, State state, NodeType type, NodeResources resources, Optional<ApplicationId> owner,
                Version currentVersion, Version wantedVersion, Version currentOsVersion, Version wantedOsVersion, ServiceState serviceState,
                long restartGeneration, long wantedRestartGeneration, long rebootGeneration, long wantedRebootGeneration,
                int cost, String canonicalFlavor, String clusterId, ClusterType clusterType) {
        this.hostname = hostname;
        this.parentHostname = parentHostname;
        this.state = state;
        this.type = type;
        this.resources = resources;
        this.owner = owner;
        this.currentVersion = currentVersion;
        this.wantedVersion = wantedVersion;
        this.currentOsVersion = currentOsVersion;
        this.wantedOsVersion = wantedOsVersion;
        this.serviceState = serviceState;
        this.restartGeneration = restartGeneration;
        this.wantedRestartGeneration = wantedRestartGeneration;
        this.rebootGeneration = rebootGeneration;
        this.wantedRebootGeneration = wantedRebootGeneration;
        this.cost = cost;
        this.canonicalFlavor = canonicalFlavor;
        this.clusterId = clusterId;
        this.clusterType = clusterType;
    }

    public HostName hostname() {
        return hostname;
    }

    public Optional<HostName> parentHostname() {
        return parentHostname;
    }

    public State state() { return state; }

    public NodeType type() {
        return type;
    }

    public NodeResources resources() {
        return resources;
    }

    public Optional<ApplicationId> owner() {
        return owner;
    }

    public Version currentVersion() {
        return currentVersion;
    }

    public Version wantedVersion() {
        return wantedVersion;
    }

    public Version currentOsVersion() {
        return currentOsVersion;
    }

    public Version wantedOsVersion() {
        return wantedOsVersion;
    }

    public ServiceState serviceState() {
        return serviceState;
    }

    public long restartGeneration() {
        return restartGeneration;
    }

    public long wantedRestartGeneration() {
        return wantedRestartGeneration;
    }

    public long rebootGeneration() {
        return rebootGeneration;
    }

    public long wantedRebootGeneration() {
        return wantedRebootGeneration;
    }

    public int cost() {
        return cost;
    }

    public String canonicalFlavor() {
        return canonicalFlavor;
    }

    public String clusterId() {
        return clusterId;
    }

    public ClusterType clusterType() {
        return clusterType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(hostname, node.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname);
    }

    /** Known node states */
    public enum State {
        provisioned,
        ready,
        reserved,
        active,
        inactive,
        dirty,
        failed,
        parked,
        unknown,
    }

    /** Known node states with regards to service orchestration */
    public enum ServiceState {
        expectedUp,
        allowedDown,
        unorchestrated
    }

    /** Known cluster types. */
    public enum ClusterType {
        admin,
        container,
        content,
        unknown
    }

    public static class Builder {
        private HostName hostname;
        private Optional<HostName> parentHostname = Optional.empty();
        private State state;
        private NodeType type;
        private NodeResources resources;
        private Optional<ApplicationId> owner = Optional.empty();
        private Version currentVersion;
        private Version wantedVersion;
        private Version currentOsVersion;
        private Version wantedOsVersion;
        private ServiceState serviceState;
        private long restartGeneration;
        private long wantedRestartGeneration;
        private long rebootGeneration;
        private long wantedRebootGeneration;
        private int cost;
        private String canonicalFlavor;
        private String clusterId;
        private ClusterType clusterType;
        
        public Builder() { }

        public Builder(Node node) {
            this.hostname = node.hostname;
            this.parentHostname = node.parentHostname;
            this.state = node.state;
            this.type = node.type;
            this.resources = node.resources;
            this.owner = node.owner;
            this.currentVersion = node.currentVersion;
            this.wantedVersion = node.wantedVersion;
            this.currentOsVersion = node.currentOsVersion;
            this.wantedOsVersion = node.wantedOsVersion;
            this.serviceState = node.serviceState;
            this.restartGeneration = node.restartGeneration;
            this.wantedRestartGeneration = node.wantedRestartGeneration;
            this.rebootGeneration = node.rebootGeneration;
            this.wantedRebootGeneration = node.wantedRebootGeneration;
            this.cost = node.cost;
            this.canonicalFlavor = node.canonicalFlavor;
            this.clusterId = node.clusterId;
            this.clusterType = node.clusterType;
        }

        public Builder hostname(HostName hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder parentHostname(HostName parentHostname) {
            this.parentHostname = Optional.ofNullable(parentHostname);
            return this;
        }

        public Builder state(State state) {
            this.state = state;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder resources(NodeResources resources) {
            this.resources = resources;
            return this;
        }

        public Builder owner(ApplicationId owner) {
            this.owner = Optional.ofNullable(owner);
            return this;
        }

        public Builder currentVersion(Version currentVersion) {
            this.currentVersion = currentVersion;
            return this;
        }

        public Builder wantedVersion(Version wantedVersion) {
            this.wantedVersion = wantedVersion;
            return this;
        }

        public Builder currentOsVersion(Version currentOsVersion) {
            this.currentOsVersion = currentOsVersion;
            return this;
        }

        public Builder wantedOsVersion(Version wantedOsVersion) {
            this.wantedOsVersion = wantedOsVersion;
            return this;
        }

        public Builder serviceState(ServiceState serviceState) {
            this.serviceState = serviceState;
            return this;
        }

        public Builder restartGeneration(long restartGeneration) {
            this.restartGeneration = restartGeneration;
            return this;
        }

        public Builder wantedRestartGeneration(long wantedRestartGeneration) {
            this.wantedRestartGeneration = wantedRestartGeneration;
            return this;
        }

        public Builder rebootGeneration(long rebootGeneration) {
            this.rebootGeneration = rebootGeneration;
            return this;
        }

        public Builder wantedRebootGeneration(long wantedRebootGeneration) {
            this.wantedRebootGeneration = wantedRebootGeneration;
            return this;
        }

        public Builder cost(int cost) {
            this.cost = cost;
            return this;
        }

        public Builder canonicalFlavor(String canonicalFlavor) {
            this.canonicalFlavor = canonicalFlavor;
            return this;
        }

        public Builder clusterId(String clusterId) {
            this.clusterId = clusterId;
            return this;
        }

        public Builder clusterType(ClusterType clusterType) {
            this.clusterType = clusterType;
            return this;
        }

        public Node build() {
            return new Node(hostname, parentHostname, state, type, resources, owner, currentVersion, wantedVersion, currentOsVersion,
                    wantedOsVersion, serviceState, restartGeneration, wantedRestartGeneration, rebootGeneration, wantedRebootGeneration,
                    cost, canonicalFlavor, clusterId, clusterType);
        }
    }
}
