// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import org.jetbrains.annotations.TestOnly;

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
    private final double vcpu;
    private final double memoryGb;
    private final double diskGb;
    private final double bandwidthGbps;
    private final boolean fastDisk;
    private final int cost;
    private final String canonicalFlavor;
    private final String clusterId;
    private final ClusterType clusterType;

    public Node(HostName hostname, Optional<HostName> parentHostname, State state, NodeType type, Optional<ApplicationId> owner,
                Version currentVersion, Version wantedVersion, Version currentOsVersion, Version wantedOsVersion, ServiceState serviceState,
                long restartGeneration, long wantedRestartGeneration, long rebootGeneration, long wantedRebootGeneration,
                double vcpu, double memoryGb, double diskGb, double bandwidthGbps, boolean fastDisk, int cost, String canonicalFlavor, String clusterId, ClusterType clusterType) {
        this.hostname = hostname;
        this.parentHostname = parentHostname;
        this.state = state;
        this.type = type;
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
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.bandwidthGbps = bandwidthGbps;
        this.fastDisk = fastDisk;
        this.cost = cost;
        this.canonicalFlavor = canonicalFlavor;
        this.clusterId = clusterId;
        this.clusterType = clusterType;
    }

    @TestOnly
    public Node(HostName hostname, Optional<HostName> parentHostname, State state, NodeType type, Optional<ApplicationId> owner,
                Version currentVersion, Version wantedVersion) {
        this(hostname, parentHostname, state, type, owner, currentVersion, wantedVersion,
             Version.emptyVersion, Version.emptyVersion, ServiceState.unorchestrated, 0, 0, 0, 0,
             2, 8, 50, 1, true, 0, "d-2-8-50", "cluster", ClusterType.container);
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

    public double vcpu() {
        return vcpu;
    }

    public double memoryGb() {
        return memoryGb;
    }

    public double diskGb() {
        return diskGb;
    }

    public double bandwidthGbps() {
        return bandwidthGbps;
    }

    public boolean fastDisk() {
        return fastDisk;
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

}
