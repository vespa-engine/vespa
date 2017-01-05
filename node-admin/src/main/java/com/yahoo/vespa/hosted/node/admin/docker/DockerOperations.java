// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.util.List;
import java.util.Optional;

public interface DockerOperations {
    Optional<String> getVespaVersion(ContainerName containerName);

    // Returns true if started
    boolean startContainerIfNeeded(ContainerNodeSpec nodeSpec);

    // Returns false if image is already downloaded
    boolean shouldScheduleDownloadOfImage(DockerImage dockerImage);

    Optional<Container> getContainer(String hostname);

    void scheduleDownloadOfImage(ContainerNodeSpec nodeSpec, Runnable callback);

    void removeContainer(ContainerNodeSpec nodeSpec, Container existingContainer, Orchestrator orchestrator);

    void executeCommandInContainer(ContainerName containerName, String[] command);

    void resumeNode(ContainerName containerName);

    void restartServicesOnNode(ContainerName containerName);

    void stopServicesOnNode(ContainerName containerName);

    void trySuspendNode(ContainerName containerName);

    Optional<Docker.ContainerStats> getContainerStats(ContainerName containerName);

    /**
     * Returns the list of containers managed by node-admin
     */
    List<Container> getAllManagedContainers();

    void deleteUnusedDockerImages();
}
