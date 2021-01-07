// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeHistory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private final DockerImage currentDockerImage;
    private final DockerImage wantedDockerImage;
    private final ServiceState serviceState;
    private final Optional<Instant> suspendedSince;
    private final Optional<Instant> currentFirmwareCheck;
    private final Optional<Instant> wantedFirmwareCheck;
    private final long restartGeneration;
    private final long wantedRestartGeneration;
    private final long rebootGeneration;
    private final long wantedRebootGeneration;
    private final int cost;
    private final String flavor;
    private final String clusterId;
    private final ClusterType clusterType;
    private final boolean wantToRetire;
    private final boolean wantToDeprovision;
    private final Optional<TenantName> reservedTo;
    private final Optional<ApplicationId> exclusiveTo;
    private final Map<String, JsonNode> reports;
    private final List<NodeHistory> history;
    private final Set<String> additionalIpAddresses;
    private final String openStackId;
    private final Optional<String> switchHostname;

    public Node(HostName hostname, Optional<HostName> parentHostname, State state, NodeType type, NodeResources resources, Optional<ApplicationId> owner,
                Version currentVersion, Version wantedVersion, Version currentOsVersion, Version wantedOsVersion,
                Optional<Instant> currentFirmwareCheck, Optional<Instant> wantedFirmwareCheck, ServiceState serviceState,
                Optional<Instant> suspendedSince, long restartGeneration, long wantedRestartGeneration, long rebootGeneration, long wantedRebootGeneration,
                int cost, String flavor, String clusterId, ClusterType clusterType, boolean wantToRetire, boolean wantToDeprovision,
                Optional<TenantName> reservedTo, Optional<ApplicationId> exclusiveTo,
                DockerImage wantedDockerImage, DockerImage currentDockerImage, Map<String, JsonNode> reports, List<NodeHistory> history,
                Set<String> additionalIpAddresses, String openStackId, Optional<String> switchHostname) {
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
        this.currentFirmwareCheck = currentFirmwareCheck;
        this.wantedFirmwareCheck = wantedFirmwareCheck;
        this.serviceState = serviceState;
        this.suspendedSince = suspendedSince;
        this.restartGeneration = restartGeneration;
        this.wantedRestartGeneration = wantedRestartGeneration;
        this.rebootGeneration = rebootGeneration;
        this.wantedRebootGeneration = wantedRebootGeneration;
        this.cost = cost;
        this.flavor = flavor;
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.wantToRetire = wantToRetire;
        this.wantToDeprovision = wantToDeprovision;
        this.reservedTo = reservedTo;
        this.exclusiveTo = exclusiveTo;
        this.wantedDockerImage = wantedDockerImage;
        this.currentDockerImage = currentDockerImage;
        this.reports = reports;
        this.history = history;
        this.openStackId = openStackId;
        this.additionalIpAddresses = additionalIpAddresses;
        this.switchHostname = switchHostname;
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

    public DockerImage currentDockerImage() {
        return currentDockerImage;
    }

    public DockerImage wantedDockerImage() {
        return wantedDockerImage;
    }

    public Optional<Instant> currentFirmwareCheck() {
        return currentFirmwareCheck;
    }

    public Optional<Instant> wantedFirmwareCheck() {
        return wantedFirmwareCheck;
    }

    public ServiceState serviceState() {
        return serviceState;
    }

    public Optional<Instant> suspendedSince() {
        return suspendedSince;
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

    public String flavor() {
        return flavor;
    }

    public String clusterId() {
        return clusterId;
    }

    public ClusterType clusterType() {
        return clusterType;
    }

    public boolean wantToRetire() {
        return wantToRetire;
    }

    public boolean wantToDeprovision() {
        return wantToDeprovision;
    }

    public Optional<TenantName> reservedTo() { return reservedTo; }

    public Optional<ApplicationId> exclusiveTo() { return exclusiveTo; }

    public Map<String, JsonNode> reports() {
        return reports;
    }

    public List<NodeHistory> history() {
        return history;
    }

    public Set<String> additionalIpAddresses() {
        return additionalIpAddresses;
    }

    public String openStackId() {
        return openStackId;
    }

    public Optional<String> switchHostname() {
        return switchHostname;
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
        breakfixed,
        unknown
    }

    /** Known node states with regards to service orchestration */
    public enum ServiceState {
        expectedUp,
        allowedDown,
        permanentlyDown,
        unorchestrated,
        unknown
    }

    /** Known cluster types. */
    public enum ClusterType {
        admin,
        container,
        content,
        combined,
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
        private DockerImage currentDockerImage;
        private DockerImage wantedDockerImage;
        private Optional<Instant> currentFirmwareCheck = Optional.empty();
        private Optional<Instant> wantedFirmwareCheck = Optional.empty();
        private ServiceState serviceState;
        private Optional<Instant> suspendedSince = Optional.empty();
        private long restartGeneration;
        private long wantedRestartGeneration;
        private long rebootGeneration;
        private long wantedRebootGeneration;
        private int cost;
        private String flavor;
        private String clusterId;
        private ClusterType clusterType;
        private boolean wantToRetire;
        private boolean wantToDeprovision;
        private Optional<TenantName> reservedTo = Optional.empty();
        private Optional<ApplicationId> exclusiveTo = Optional.empty();
        private Map<String, JsonNode> reports = new HashMap<>();
        private List<NodeHistory> history = new ArrayList<>();
        private Set<String> additionalIpAddresses = new HashSet<>();
        private String openStackId;
        private Optional<String> switchHostname = Optional.empty();

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
            this.currentDockerImage = node.currentDockerImage;
            this.wantedDockerImage = node.wantedDockerImage;
            this.currentFirmwareCheck = node.currentFirmwareCheck;
            this.wantedFirmwareCheck = node.wantedFirmwareCheck;
            this.serviceState = node.serviceState;
            this.suspendedSince = node.suspendedSince;
            this.restartGeneration = node.restartGeneration;
            this.wantedRestartGeneration = node.wantedRestartGeneration;
            this.rebootGeneration = node.rebootGeneration;
            this.wantedRebootGeneration = node.wantedRebootGeneration;
            this.cost = node.cost;
            this.flavor = node.flavor;
            this.clusterId = node.clusterId;
            this.clusterType = node.clusterType;
            this.wantToRetire = node.wantToRetire;
            this.wantToDeprovision = node.wantToDeprovision;
            this.reservedTo = node.reservedTo;
            this.exclusiveTo = node.exclusiveTo;
            this.reports = node.reports;
            this.history = node.history;
            this.additionalIpAddresses = node.additionalIpAddresses;
            this.openStackId = node.openStackId;
            this.switchHostname = node.switchHostname;
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

        public Builder currentDockerImage(DockerImage currentDockerImage) {
            this.currentDockerImage = currentDockerImage;
            return this;
        }

        public Builder wantedDockerImage(DockerImage wantedDockerImage) {
            this.wantedDockerImage = wantedDockerImage;
            return this;
        }

        public Builder currentFirmwareCheck(Instant currentFirmwareCheck) {
            this.currentFirmwareCheck = Optional.ofNullable(currentFirmwareCheck);
            return this;
        }

        public Builder wantedFirmwareCheck(Instant wantedFirmwareCheck) {
            this.wantedFirmwareCheck = Optional.ofNullable(wantedFirmwareCheck);
            return this;
        }

        public Builder serviceState(ServiceState serviceState) {
            this.serviceState = serviceState;
            return this;
        }

        public Builder suspendedSince(Instant suspendedSince) {
            this.suspendedSince = Optional.ofNullable(suspendedSince);
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

        public Builder flavor(String flavor) {
            this.flavor = flavor;
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

        public Builder wantToRetire(boolean wantToRetire) {
            this.wantToRetire = wantToRetire;
            return this;
        }

        public Builder wantToDeprovision(boolean wantToDeprovision) {
            this.wantToDeprovision = wantToDeprovision;
            return this;
        }

        public Builder reservedTo(TenantName tenant) {
            this.reservedTo = Optional.of(tenant);
            return this;
        }

        public Builder exclusiveTo(ApplicationId exclusiveTo) {
            this.exclusiveTo = Optional.of(exclusiveTo);
            return this;
        }

        public Builder history(List<NodeHistory> history) {
            this.history = history;
            return this;
        }

        public Builder additionalIpAddresses(Set<String> additionalIpAddresses) {
            this.additionalIpAddresses = additionalIpAddresses;
            return this;
        }

        public Builder openStackId(String openStackId) {
            this.openStackId = openStackId;
            return this;
        }

        public Builder switchHostname(String switchHostname) {
            this.switchHostname = Optional.ofNullable(switchHostname);
            return this;
        }

        public Node build() {
            return new Node(hostname, parentHostname, state, type, resources, owner, currentVersion, wantedVersion,
                            currentOsVersion, wantedOsVersion, currentFirmwareCheck, wantedFirmwareCheck, serviceState,
                            suspendedSince, restartGeneration, wantedRestartGeneration, rebootGeneration, wantedRebootGeneration,
                            cost, flavor, clusterId, clusterType, wantToRetire, wantToDeprovision, reservedTo, exclusiveTo,
                            wantedDockerImage, currentDockerImage, reports, history, additionalIpAddresses, openStackId, switchHostname);
        }

    }
}
