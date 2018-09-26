// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ContainerStats;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;

import java.util.List;
import java.util.Optional;

public interface DockerOperations {

    void createContainer(ContainerName containerName, NodeSpec node, ContainerData containerData);

    void startContainer(ContainerName containerName);

    void removeContainer(Container existingContainer);

    Optional<Container> getContainer(ContainerName containerName);

    boolean pullImageAsyncIfNeeded(DockerImage dockerImage);

    ProcessResult executeCommandInContainerAsRoot(ContainerName containerName, String... command);

    ProcessResult executeCommandInContainerAsRoot(ContainerName containerName, Long timeoutSeconds, String... command);

    ProcessResult executeCommandInNetworkNamespace(ContainerName containerName, String... command);


    /** Resume node. Resuming a node means that it is ready to take on traffic. */
    void resumeNode(ContainerName containerName);

    /**
     * Suspend node. Suspending a node means the node should be taken temporarly offline,
     * such that maintenance of the node can be done (upgrading, rebooting, etc).
     */
    void suspendNode(ContainerName containerName);

    void restartVespa(ContainerName containerName);

    void stopServices(ContainerName containerName);

    Optional<ContainerStats> getContainerStats(ContainerName containerName);

    /**
     * Returns the list of containers managed by node-admin
     */
    List<Container> getAllManagedContainers();

    void deleteUnusedDockerImages();
}
