// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;

import java.util.Objects;
import java.util.Optional;

/**
 * @author stiankri
 */
public class ContainerNodeSpec {
    public final HostName hostname;
    public final Optional<DockerImage> wantedDockerImage;
    public final ContainerName containerName;
    public final NodeState nodeState;
    public final Optional<Long> wantedRestartGeneration;
    public final Optional<Long> currentRestartGeneration;
    public final Optional<Double> minCpuCores;
    public final Optional<Double> minMainMemoryAvailableGb;
    public final Optional<Double> minDiskAvailableGb;

    public ContainerNodeSpec(
            final HostName hostname,
            final Optional<DockerImage> wantedDockerImage,
            final ContainerName containerName,
            final NodeState nodeState,
            final Optional<Long> wantedRestartGeneration,
            final Optional<Long> currentRestartGeneration,
            final Optional<Double> minCpuCores,
            final Optional<Double> minMainMemoryAvailableGb,
            final Optional<Double> minDiskAvailableGb) {
        this.hostname = hostname;
        this.wantedDockerImage = wantedDockerImage;
        this.containerName = containerName;
        this.nodeState = nodeState;
        this.wantedRestartGeneration = wantedRestartGeneration;
        this.currentRestartGeneration = currentRestartGeneration;
        this.minCpuCores = minCpuCores;
        this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
        this.minDiskAvailableGb = minDiskAvailableGb;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainerNodeSpec)) return false;

        ContainerNodeSpec that = (ContainerNodeSpec) o;

        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(wantedDockerImage, that.wantedDockerImage) &&
                Objects.equals(containerName, that.containerName) &&
                Objects.equals(nodeState, that.nodeState) &&
                Objects.equals(wantedRestartGeneration, that.wantedRestartGeneration) &&
                Objects.equals(currentRestartGeneration, that.currentRestartGeneration) &&
                Objects.equals(minCpuCores, that.minCpuCores) &&
                Objects.equals(minMainMemoryAvailableGb, that.minMainMemoryAvailableGb) &&
                Objects.equals(minDiskAvailableGb, that.minDiskAvailableGb);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                hostname,
                wantedDockerImage,
                containerName,
                nodeState,
                wantedRestartGeneration,
                currentRestartGeneration,
                minCpuCores,
                minMainMemoryAvailableGb,
                minDiskAvailableGb);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " hostname=" + hostname
                + " wantedDockerImage=" + wantedDockerImage
                + " containerName=" + containerName
                + " nodeState=" + nodeState
                + " wantedRestartGeneration=" + wantedRestartGeneration
                + " minCpuCores=" + minCpuCores
                + " currentRestartGeneration=" + currentRestartGeneration
                + " minMainMemoryAvailableGb=" + minMainMemoryAvailableGb
                + " minDiskAvailableGb=" + minDiskAvailableGb
                + " }";
    }
}
