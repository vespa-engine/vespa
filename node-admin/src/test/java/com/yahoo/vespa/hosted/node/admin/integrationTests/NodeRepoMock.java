package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mock with some simple logic
 * @author dybis
 */
public class NodeRepoMock implements NodeRepository {

    public static final List<ContainerNodeSpec> containerNodeSpecs = new ArrayList<>();
    public static StringBuilder requests = new StringBuilder();

    public NodeRepoMock() {
        if(OrchestratorMock.semaphore.tryAcquire()) {
            throw new RuntimeException("OrchestratorMock.semaphore must be acquired before using NodeRepoMock");
        }
    }

    @Override
    public List<ContainerNodeSpec> getContainersToRun() throws IOException {
        return containerNodeSpecs;
    }

    @Override
    public Optional<ContainerNodeSpec> getContainerNodeSpec(HostName hostName) throws IOException {
        return containerNodeSpecs.stream()
                .filter(containerNodeSpec -> containerNodeSpec.hostname.equals(hostName))
                .findFirst();
    }

    @Override
    public void updateNodeAttributes(HostName hostName, long restartGeneration, DockerImage dockerImage, String containerVespaVersion) throws IOException {
        requests.append("updateNodeAttributes with HostName: ").append(hostName)
                .append(", restartGeneration: ").append(restartGeneration)
                .append(", DockerImage: ").append(dockerImage)
                .append(", containerVespaVersion: ").append(containerVespaVersion).append("\n");
    }

    @Override
    public void markAsReady(HostName hostName) throws IOException {

    }
}
