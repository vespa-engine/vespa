// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Mock with some simple logic
 *
 * @author freva
 */
public class DockerMock implements Docker {
    private List<Container> containers = new ArrayList<>();
    public final CallOrderVerifier callOrderVerifier;
    private static final Object monitor = new Object();

    public DockerMock(CallOrderVerifier callOrderVerifier) {
        this.callOrderVerifier = callOrderVerifier;
    }

    @Override
    public CreateContainerCommand createContainerCommand(
            DockerImage dockerImage,
            ContainerName containerName,
            String hostName) {
        synchronized (monitor) {
            callOrderVerifier.add("createContainerCommand with " + dockerImage +
                    ", HostName: " + hostName + ", " + containerName);
            containers.add(new Container(hostName, dockerImage, containerName, Container.State.RUNNING, 2));
        }

        return new StartContainerCommandMock();
    }

    @Override
    public void connectContainerToNetwork(ContainerName containerName, String networkName) {
        synchronized (monitor) {
            callOrderVerifier.add("Connecting " + containerName + " to network: " + networkName);
        }
    }

    @Override
    public void copyArchiveToContainer(String sourcePath, ContainerName destinationContainer, String destinationPath) {

    }

    @Override
    public List<Container> getAllContainersManagedBy(String manager) {
        synchronized (monitor) {
            return new ArrayList<>(containers);
        }
    }

    @Override
    public List<ContainerName> listAllContainersManagedBy(String manager) {
        synchronized (monitor) {
            return containers.stream().map(container -> container.name).collect(Collectors.toList());
        }
    }

    @Override
    public Optional<ContainerStats> getContainerStats(ContainerName containerName) {
        return Optional.empty();
    }

    @Override
    public void startContainer(ContainerName containerName) {
        synchronized (monitor) {
            callOrderVerifier.add("startContainer with " + containerName);
        }
    }

    @Override
    public void stopContainer(ContainerName containerName) {
        synchronized (monitor) {
            callOrderVerifier.add("stopContainer with " + containerName);
            containers = containers.stream()
                    .map(container -> container.name.equals(containerName) ?
                            new Container(container.hostname, container.image, container.name, Container.State.EXITED, 0) : container)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        synchronized (monitor) {
            callOrderVerifier.add("deleteContainer with " + containerName);
            containers = containers.stream()
                    .filter(container -> !container.name.equals(containerName))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public Optional<Container> getContainer(ContainerName containerName) {
        synchronized (monitor) {
            return containers.stream().filter(container -> container.name.equals(containerName)).findFirst();
        }
    }

    @Override
    public CompletableFuture<DockerImage> pullImageAsync(DockerImage image) {
        synchronized (monitor) {
            callOrderVerifier.add("pullImageAsync with " + image);
            final CompletableFuture<DockerImage> completableFuture = new CompletableFuture<>();
            new Thread() {
                public void run() {
                    try {
                        Thread.sleep(500);
                        completableFuture.complete(image);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }.start();
            return completableFuture;
        }
    }

    @Override
    public boolean imageIsDownloaded(DockerImage image) {
        return true;
    }

    @Override
    public void deleteImage(DockerImage dockerImage) {

    }

    @Override
    public void buildImage(File dockerfile, DockerImage dockerImage) {

    }

    @Override
    public void deleteUnusedDockerImages() {

    }

    @Override
    public ProcessResult executeInContainer(ContainerName containerName, String... args) {
        synchronized (monitor) {
            callOrderVerifier.add("executeInContainer with " + containerName + ", args: " + Arrays.toString(args));
        }
        return new ProcessResult(0, null, "");
    }

    @Override
    public ProcessResult executeInContainerAsRoot(ContainerName containerName, String... args) {
        synchronized (monitor) {
            callOrderVerifier.add("executeInContainerAsRoot with " + containerName + ", args: " + Arrays.toString(args));
        }
        return new ProcessResult(0, null, "");
    }

    @Override
    public ProcessResult executeInContainerAsRoot(ContainerName containerName, Long timeoutSeconds, String... args) {
        synchronized (monitor) {
            callOrderVerifier.add("executeInContainerAsRoot with " + containerName + ", args: " + Arrays.toString(args));
        }
        return new ProcessResult(0, null, "");
    }


    public static class StartContainerCommandMock implements CreateContainerCommand {
        @Override
        public CreateContainerCommand withLabel(String name, String value) {
            return this;
        }

        @Override
        public CreateContainerCommand withEnvironment(String name, String value) {
            return this;
        }

        @Override
        public CreateContainerCommand withVolume(String path, String volumePath) {
            return this;
        }

        @Override
        public CreateContainerCommand withMemoryInMb(long megaBytes) {
            return this;
        }

        @Override
        public CreateContainerCommand withCpuShares(int shares) {
            return this;
        }

        @Override
        public CreateContainerCommand withNetworkMode(String mode) {
            return this;
        }

        @Override
        public CreateContainerCommand withIpAddress(InetAddress address) {
            return this;
        }

        @Override
        public CreateContainerCommand withUlimit(String name, int softLimit, int hardLimit) {
            return this;
        }

        @Override
        public CreateContainerCommand withEntrypoint(String... entrypoint) {
            return this;
        }

        @Override
        public CreateContainerCommand withManagedBy(String manager) {
            return this;
        }

        @Override
        public CreateContainerCommand withAddCapability(String capabilityName) {
            return this;
        }

        @Override
        public CreateContainerCommand withDropCapability(String capabilityName) {
            return this;
        }

        @Override
        public void create() {

        }
    }
}
