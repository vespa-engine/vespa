// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.applicationmodel.HostName;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author stiankri
 */
public interface Docker {

    void startContainer(DockerImage dockerImage, HostName hostName, ContainerName containerName, double minCpuCores, double minDiskAvailableGb, double minMainMemoryAvailableGb);

    void stopContainer(ContainerName containerName);

    void deleteContainer(ContainerName containerName);

    List<Container> getAllManagedContainers();

    Optional<Container> getContainer(HostName hostname);

    CompletableFuture<DockerImage> pullImageAsync(DockerImage image);

    boolean imageIsDownloaded(DockerImage image);

    void deleteApplicationStorage(ContainerName containerName) throws IOException;

    String getVespaVersion(ContainerName containerName);

    void deleteImage(DockerImage dockerImage);

    /**
     * Returns the local images that are currently not in use by any container.
     */
    Set<DockerImage> getUnusedDockerImages();

    /**
     * TODO: Make this function interruptible, see https://github.com/spotify/docker-client/issues/421
     *
     * @param args          Program arguments. args[0] must be the program filename.
     * @throws RuntimeException  (or some subclass thereof) on failure, including docker failure, command failure
     */
    ProcessResult executeInContainer(ContainerName containerName, String... args);
}
