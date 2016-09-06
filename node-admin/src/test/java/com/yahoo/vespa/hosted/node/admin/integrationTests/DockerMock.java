// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;

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
    private List<Container> containers = new ArrayList<>();
    public final CallOrderVerifier callOrder;
    private static final Object monitor = new Object();

    public DockerMock(CallOrderVerifier callOrder) {
        this.callOrder = callOrder;
    }

    @Override
    public StartContainerCommand createStartContainerCommand(
            DockerImage dockerImage,
            ContainerName containerName,
            HostName hostName) {
        synchronized (monitor) {
            callOrder.add("createStartContainerCommand with DockerImage: " + dockerImage + ", HostName: " + hostName +
                    ", ContainerName: " + containerName);
            containers.add(new Container(hostName, dockerImage, containerName, true));
        }

        return new StartContainerCommandMock();
    }

    @Override
    public ContainerInfo inspectContainer(ContainerName containerName) {
        return () -> Optional.of(2);
    }

    @Override
    public void stopContainer(ContainerName containerName) {
        synchronized (monitor) {
            callOrder.add("stopContainer with ContainerName: " + containerName);
            containers = containers.stream()
                    .map(container -> container.name.equals(containerName) ?
                            new Container(container.hostname, container.image, container.name, false) : container)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void deleteContainer(ContainerName containerName) {
        synchronized (monitor) {
            callOrder.add("deleteContainer with ContainerName: " + containerName);
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
            callOrder.add("pullImageAsync with DockerImage: " + image);
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
    public void deleteUnusedDockerImages(Set<DockerImage> except) {

    }

    @Override
    public ProcessResult executeInContainer(ContainerName containerName, String... args) {
        synchronized (monitor) {
            callOrder.add("executeInContainer with ContainerName: " + containerName +
                    ", args: " + Arrays.toString(args));
        }
        return new ProcessResult(0, null, "");
    }


    public static class StartContainerCommandMock implements StartContainerCommand {
        @Override
        public StartContainerCommand withLabel(String name, String value) {
            return this;
        }

        @Override
        public StartContainerCommand withEnvironment(String name, String value) {
            return this;
        }

        @Override
        public StartContainerCommand withVolume(String path, String volumePath) {
            return this;
        }

        @Override
        public StartContainerCommand withMemoryInMb(long megaBytes) {
            return this;
        }

        @Override
        public StartContainerCommand withNetworkMode(String mode) {
            return this;
        }

        @Override
        public StartContainerCommand withIpv6Address(String address) {
            return this;
        }

        @Override
        public void start() {

        }
    }
}
