// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;

import java.util.List;
import java.util.Optional;

public interface DockerOperations {

    void createContainer(ContainerName containerName, ContainerNodeSpec nodeSpec);

    void startContainer(ContainerName containerName, ContainerNodeSpec nodeSpec);

    void removeContainer(Container existingContainer, ContainerNodeSpec nodeSpec);

    Optional<Container> getContainer(ContainerName containerName);

    boolean pullImageAsyncIfNeeded(DockerImage dockerImage);

    ProcessResult executeCommandInContainerAsRoot(ContainerName containerName, String... command);

    ProcessResult executeCommandInContainerAsRoot(ContainerName containerName, Long timeoutSeconds, String... command);

    void executeCommandInNetworkNamespace(ContainerName containerName, String... command);

    void resumeNode(ContainerName containerName);

    void restartVespaOnNode(ContainerName containerName);

    void stopServicesOnNode(ContainerName containerName);

    void trySuspendNode(ContainerName containerName);

    Optional<Docker.ContainerStats> getContainerStats(ContainerName containerName);

    /**
     * Returns the list of containers managed by node-admin
     */
    List<Container> getAllManagedContainers();

    List<ContainerName> listAllManagedContainers();

    void deleteUnusedDockerImages();
}
