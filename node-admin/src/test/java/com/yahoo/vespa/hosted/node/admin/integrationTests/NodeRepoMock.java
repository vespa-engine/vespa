// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class NodeRepoMock implements NodeRepository {
    private List<ContainerNodeSpec> containerNodeSpecs = new ArrayList<>();
    private final CallOrderVerifier callOrder;

    private static final Object monitor = new Object();

    public NodeRepoMock(CallOrderVerifier callOrder) {
        this.callOrder = callOrder;
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun() throws IOException {
        synchronized (monitor) {
            return containerNodeSpecs;
        }
    }

    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(HostName hostName) throws IOException {
        synchronized (monitor) {
            return containerNodeSpecs.stream()
                    .filter(containerNodeSpec -> containerNodeSpec.hostname.equals(hostName))
                    .findFirst();
        }
    }

    @Override
    public void updateNodeAttributes(HostName hostName, long restartGeneration, DockerImage dockerImage, String containerVespaVersion) throws IOException {
        synchronized (monitor) {
            callOrder.add("updateNodeAttributes with HostName: " + hostName + ", restartGeneration: " + restartGeneration +
                    ", DockerImage: " + dockerImage + ", containerVespaVersion: " + containerVespaVersion);
        }
    }

    @Override
    public void markAsReady(HostName hostName) throws IOException {
        Optional<ContainerNodeSpec> cns = getContainerNodeSpec(hostName);

        synchronized (monitor) {
            if (cns.isPresent()) {
                updateContainerNodeSpec(cns.get().hostname,
                        cns.get().wantedDockerImage, cns.get().containerName, NodeState.READY,
                        cns.get().wantedRestartGeneration, cns.get().currentRestartGeneration,
                        cns.get().minCpuCores, cns.get().minMainMemoryAvailableGb, cns.get().minDiskAvailableGb);
            }
            callOrder.add("markAsReady with HostName: " + hostName);
        }
    }

    public void updateContainerNodeSpec(HostName hostName,
                                               Optional<DockerImage> wantedDockerImage,
                                               ContainerName containerName,
                                               NodeState nodeState,
                                               Optional<Long> wantedRestartGeneration,
                                               Optional<Long> currentRestartGeneration,
                                               Optional<Double> minCpuCores,
                                               Optional<Double> minMainMemoryAvailableGb,
                                               Optional<Double> minDiskAvailableGb) {
        addContainerNodeSpec(new ContainerNodeSpec(hostName,
                wantedDockerImage, containerName, nodeState,
                wantedRestartGeneration, currentRestartGeneration,
                minCpuCores, minMainMemoryAvailableGb, minDiskAvailableGb));
    }

    public void addContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        removeContainerNodeSpec(containerNodeSpec.hostname);
        synchronized (monitor) {
            containerNodeSpecs.add(containerNodeSpec);
        }
    }

    public void clearContainerNodeSpecs() {
        synchronized (monitor) {
            containerNodeSpecs.clear();
        }
    }

    public void removeContainerNodeSpec(HostName hostName) {
        synchronized (monitor) {
            containerNodeSpecs = containerNodeSpecs.stream()
                    .filter(c -> !c.hostname.equals(hostName))
                    .collect(Collectors.toList());
        }
    }

    public int getNumberOfContainerSpecs() {
        synchronized (monitor) {
            return containerNodeSpecs.size();
        }
    }
}
