package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
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

    private static List<ContainerNodeSpec> containerNodeSpecs;
    private static StringBuilder requests;

    private static final Object monitor = new Object();

    static {
        reset();
    }

    public NodeRepoMock() {
        if (OrchestratorMock.semaphore.tryAcquire()) {
            throw new RuntimeException("OrchestratorMock.semaphore must be acquired before using NodeRepoMock");
        }
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
            requests.append("updateNodeAttributes with HostName: ").append(hostName)
                    .append(", restartGeneration: ").append(restartGeneration)
                    .append(", DockerImage: ").append(dockerImage)
                    .append(", containerVespaVersion: ").append(containerVespaVersion).append("\n");
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
            requests.append("markAsReady with HostName: ").append(hostName).append("\n");
        }
    }

    public static void updateContainerNodeSpec(HostName hostName,
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

    public static void addContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        removeContainerNodeSpec(containerNodeSpec.hostname);
        synchronized (monitor) {
            containerNodeSpecs.add(containerNodeSpec);
        }
    }

    public static void clearContainerNodeSpecs() {
        synchronized (monitor) {
            containerNodeSpecs.clear();
        }
    }

    public static void removeContainerNodeSpec(HostName hostName) {
        synchronized (monitor) {
            containerNodeSpecs = containerNodeSpecs.stream()
                    .filter(c -> !c.hostname.equals(hostName))
                    .collect(Collectors.toList());
        }
    }

    public static int getNumberOfContainerSpecs() {
        synchronized (monitor) {
            return containerNodeSpecs.size();
        }
    }

    public static String getRequests() {
        synchronized (monitor) {
            return requests.toString();
        }
    }

    public static void reset() {
        synchronized (monitor) {
            containerNodeSpecs = new ArrayList<>();
            requests = new StringBuilder();
        }
    }
}
