// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.docker.Container;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.ProcessResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Mock with some simple logic
 *
 * @author valerijf
 */
public class DockerMock implements Docker {
    private List<Container> containers;
    private static StringBuilder requests;

    private static final Object monitor = new Object();

    static {
        reset();
    }

    public DockerMock() {
        if (OrchestratorMock.semaphore.tryAcquire()) {
            throw new RuntimeException("OrchestratorMock.semaphore must be acquired before using DockerMock");
        }

        containers = new ArrayList<>();
    }

    @Override
    public void startContainer(DockerImage dockerImage, HostName hostName, ContainerName containerName,
                               double minCpuCores, double minDiskAvailableGb, double minMainMemoryAvailableGb) {
        synchronized (monitor) {
            requests.append("startContainer with DockerImage: ").append(dockerImage).append(", HostName: ").append(hostName)
                    .append(", ContainerName: ").append(containerName).append(", minCpuCores: ").append(minCpuCores)
                    .append(", minDiskAvailableGb: ").append(minDiskAvailableGb).append(", minMainMemoryAvailableGb: ")
                    .append(minMainMemoryAvailableGb).append("\n");
            containers.add(new Container(hostName, dockerImage, containerName, true));
        }
    }

    @Override
    public void stopContainer(ContainerName containerName) {
        synchronized (monitor) {
            requests.append("stopContainer with ContainerName: ").append(containerName).append("\n");
            containers = containers.stream()
                    .map(container -> container.name.equals(containerName) ?
                            new Container(container.hostname, container.image, container.name, false) : container)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        synchronized (monitor) {
            requests.append("deleteContainer with ContainerName: ").append(containerName).append("\n");
            containers = containers.stream()
                    .filter(container -> !container.name.equals(containerName))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<Container> getAllManagedContainers() {
        synchronized (monitor) {
            return new ArrayList<>(containers);
        }
    }

    @Override
    public Optional<Container> getContainer(HostName hostname) {
        synchronized (monitor) {
            return containers.stream().filter(container -> container.hostname.equals(hostname)).findFirst();
        }
    }

    @Override
    public CompletableFuture<DockerImage> pullImageAsync(DockerImage image) {
        synchronized (monitor) {
            requests.append("pullImageAsync with DockerImage: ").append(image);
            return null;
        }
    }

    @Override
    public boolean imageIsDownloaded(DockerImage image) {
        return true;
    }

    @Override
    public void deleteApplicationStorage(ContainerName containerName) throws IOException {
        synchronized (monitor) {
            requests.append("deleteApplicationStorage with ContainerName: ").append(containerName).append("\n");
        }
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
        synchronized (monitor) {
            requests.append("executeInContainer with ContainerName: ").append(containerName)
                    .append(", args: ").append(Arrays.toString(args)).append("\n");
        }
        return new ProcessResult(0, "OK");
    }

    public static String getRequests() {
        synchronized (monitor) {
            return requests.toString();
        }
    }

    public static void reset() {
        synchronized (monitor) {
            requests = new StringBuilder();
        }
    }
}

