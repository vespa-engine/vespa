package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.docker.Container;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.ProcessResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Mock with some simple logic
 * @author valerijf
 */
public class DockerMock implements Docker {
    private List<Container> containers = new ArrayList<>();
    public static StringBuilder requests = new StringBuilder();

    public DockerMock() {
        if(OrchestratorMock.semaphore.tryAcquire()) {
            throw new RuntimeException("OrchestratorMock.semaphore must be acquired before using DockerMock");
        }
    }

    @Override
    public void startContainer(DockerImage dockerImage, HostName hostName, ContainerName containerName,
                               double minCpuCores, double minDiskAvailableGb, double minMainMemoryAvailableGb) {
        requests.append("startContainer with DockerImage: ").append(dockerImage).append(", HostName: ").append(hostName)
                .append(", ContainerName: ").append(containerName).append(", minCpuCores: ").append(minCpuCores)
                .append(", minDiskAvailableGb: ").append(minDiskAvailableGb).append(", minMainMemoryAvailableGb: ")
                .append(minMainMemoryAvailableGb).append("\n");
        containers.add(new Container(hostName, dockerImage, containerName, true));
    }

    @Override
    public void stopContainer(ContainerName containerName) {
        containers = containers.stream()
                .map(container -> container.name.equals(containerName) ?
                        new Container(container.hostname, container.image, container.name, false) : container)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        requests.append("deleteContainer with ContainerName: ").append(containerName);
        containers = containers.stream()
                .filter(container -> !container.name.equals(containerName))
                .collect(Collectors.toList());
    }

    @Override
    public List<Container> getAllManagedContainers() {
        return new ArrayList<>(containers);
    }

    @Override
    public Optional<Container> getContainer(HostName hostname) {
        return containers.stream().filter(container -> container.hostname.equals(hostname)).findFirst();
    }

    @Override
    public CompletableFuture<DockerImage> pullImageAsync(DockerImage image) {
        requests.append("pullImageAsync with DockerImage: ").append(image);
        return null;
    }

    @Override
    public boolean imageIsDownloaded(DockerImage image) {
        return true;
    }

    @Override
    public void deleteApplicationStorage(ContainerName containerName) throws IOException {

    }

    @Override
    public String getVespaVersion(ContainerName containerName) {
        return null;
    }

    @Override
    public void deleteImage(DockerImage dockerImage) {

    }

    @Override
    public Set<DockerImage> getUnusedDockerImages() {
        return new HashSet<>();
    }

    @Override
    public ProcessResult executeInContainer(ContainerName containerName, String... args) {
        return new ProcessResult(0, "OK");
    }
}

