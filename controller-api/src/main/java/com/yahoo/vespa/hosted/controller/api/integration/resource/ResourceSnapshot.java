// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the resources allocated to a deployment at a specific point in time.
 *
 * @author olaa
 */
public class ResourceSnapshot {

    private final ApplicationId applicationId;
    private final ResourceAllocation resourceAllocation;
    private final Instant timestamp;
    private final ZoneId zoneId;

    public ResourceSnapshot(ApplicationId applicationId, double cpuCores, double memoryGb, double diskGb, NodeResources.Architecture architecture, Instant timestamp, ZoneId zoneId) {
        this.applicationId = applicationId;
        this.resourceAllocation = new ResourceAllocation(cpuCores, memoryGb, diskGb, architecture);
        this.timestamp = timestamp;
        this.zoneId = zoneId;
    }

    public static ResourceSnapshot from(ApplicationId applicationId, int nodes, double cpuCores, double memoryGb, double diskGb, NodeResources.Architecture architecture, Instant timestamp, ZoneId zoneId) {
        return new ResourceSnapshot(applicationId, cpuCores * nodes, memoryGb * nodes, diskGb * nodes, architecture, timestamp, zoneId);
    }

    public static ResourceSnapshot from(List<Node> nodes, Instant timestamp, ZoneId zoneId) {
        Set<ApplicationId> applicationIds = nodes.stream()
                                                 .filter(node -> node.owner().isPresent())
                                                 .map(node -> node.owner().get())
                                                 .collect(Collectors.toSet());

        if (applicationIds.size() != 1) throw new IllegalArgumentException("List of nodes can only represent one application");

        return new ResourceSnapshot(
                applicationIds.iterator().next(),
                nodes.stream().map(Node::resources).mapToDouble(NodeResources::vcpu).sum(),
                nodes.stream().map(Node::resources).mapToDouble(NodeResources::memoryGb).sum(),
                nodes.stream().map(Node::resources).mapToDouble(NodeResources::diskGb).sum(),
                nodes.stream().map(node -> node.resources().architecture()).findFirst().orElse(NodeResources.Architecture.getDefault()),
                timestamp,
                zoneId
        );
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    public ResourceAllocation allocation() {
        return resourceAllocation;
    }

    public double getCpuCores() {
        return resourceAllocation.getCpuCores();
    }

    public double getMemoryGb() {
        return resourceAllocation.getMemoryGb();
    }

    public double getDiskGb() {
        return resourceAllocation.getDiskGb();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public NodeResources.Architecture getArchitecture() {
        return resourceAllocation.getArchitecture();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceSnapshot)) return false;

        ResourceSnapshot other = (ResourceSnapshot) o;
        return this.applicationId.equals(other.applicationId) &&
                this.resourceAllocation.equals(other.resourceAllocation) &&
                this.timestamp.equals(other.timestamp) &&
                this.zoneId.equals(other.zoneId);
    }

    @Override
    public int hashCode(){
        return Objects.hash(applicationId, resourceAllocation, timestamp, zoneId);
    }
}
