// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.yahoo.vespa.applicationmodel.HostName;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * API to simplify the com.github.dockerjava API for clients,
 * and to avoid OSGi exporting those classes.
 */
public interface Docker {
    interface StartContainerCommand {
        StartContainerCommand withLabel(String name, String value);
        StartContainerCommand withEnvironment(String name, String value);
        StartContainerCommand withVolume(String path, String volumePath);
        StartContainerCommand withMemoryInMb(long megaBytes);
        StartContainerCommand withNetworkMode(String mode);
        StartContainerCommand withIpv6Address(String address);
        void start();
    }

    StartContainerCommand createStartContainerCommand(
            DockerImage dockerImage,
            ContainerName containerName,
            HostName hostName);

    void stopContainer(ContainerName containerName);

    void deleteContainer(ContainerName containerName);

    List<Container> getAllManagedContainers();

    Optional<Container> getContainer(HostName hostname);

    CompletableFuture<DockerImage> pullImageAsync(DockerImage image);

    boolean imageIsDownloaded(DockerImage image);

    void deleteImage(DockerImage dockerImage);

    /**
     * Deletes the local images that are currently not in use by any container and not in the except set.
     */
    void deleteUnusedDockerImages(Set<DockerImage> except);

    /**
     * TODO: Make this function interruptible, see https://github.com/spotify/docker-client/issues/421
     *
     * @param args          Program arguments. args[0] must be the program filename.
     * @throws RuntimeException  (or some subclass thereof) on failure, including docker failure, command failure
     */
    ProcessResult executeInContainer(ContainerName containerName, String... args);

    interface ContainerInfo {
        /** returns Optional.empty() if not running. */
        Optional<Integer> getPid();
    }

    ContainerInfo inspectContainer(ContainerName containerName);
}
