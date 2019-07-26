// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ResourceSnapshot {

    private ApplicationId applicationId;
    private double cpuCores;
    private double memoryGb;
    private double diskGb;
    private Instant timestamp;

    public ResourceSnapshot(ApplicationId applicationId, double cpuCores, double memoryGb, double diskGb, Instant timestamp) {
        this.applicationId = applicationId;
        this.cpuCores = cpuCores;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.timestamp = timestamp;
    }

    public static ResourceSnapshot from(List<NodeRepositoryNode> nodes, Instant timestamp) {
        Set<ApplicationId> applicationIds = nodes.stream()
                .map(n -> ApplicationId.from(n.getOwner().tenant, n.getOwner().application, n.getOwner().instance))
                .collect(Collectors.toSet());

        if (applicationIds.size() != 1) throw new IllegalArgumentException("List of nodes can only represent one application");

        return new ResourceSnapshot(
                applicationIds.iterator().next(),
                nodes.stream().mapToDouble(NodeRepositoryNode::getMinCpuCores).sum(),
                nodes.stream().mapToDouble(NodeRepositoryNode::getMinMainMemoryAvailableGb).sum(),
                nodes.stream().mapToDouble(NodeRepositoryNode::getMinDiskAvailableGb).sum(),
                timestamp
        );
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    public double getCpuCores() {
        return cpuCores;
    }

    public double getMemoryGb() {
        return memoryGb;
    }

    public double getDiskGb() {
        return diskGb;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setApplicationId(ApplicationId applicationId) {
        this.applicationId = applicationId;
    }

    public void setCpuCores(double cpuCores) {
        this.cpuCores = cpuCores;
    }

    public void setMemoryGb(double memoryGb) {
        this.memoryGb = memoryGb;
    }

    public void setDiskGb(double diskGb) {
        this.diskGb = diskGb;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
