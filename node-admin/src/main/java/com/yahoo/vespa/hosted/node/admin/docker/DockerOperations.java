// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerResources;
import com.yahoo.vespa.hosted.dockerapi.ContainerStats;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface DockerOperations {

    void createContainer(NodeAgentContext context, ContainerData containerData, ContainerResources containerResources);

    void startContainer(NodeAgentContext context);

    void removeContainer(NodeAgentContext context, Container container);

    void updateContainer(NodeAgentContext context, ContainerResources containerResources);

    Optional<Container> getContainer(NodeAgentContext context);

    boolean pullImageAsyncIfNeeded(DockerImage dockerImage);

    ProcessResult executeCommandInContainerAsRoot(NodeAgentContext context, String... command);

    ProcessResult executeCommandInContainerAsRoot(NodeAgentContext context, Long timeoutSeconds, String... command);

    /** Executes a command in inside containers network namespace, throws on non-zero exit code */
    CommandResult executeCommandInNetworkNamespace(NodeAgentContext context, String... command);


    /** Resume node. Resuming a node means that it is ready to take on traffic. */
    void resumeNode(NodeAgentContext context);

    /**
     * Suspend node. Suspending a node means the node should be taken temporarly offline,
     * such that maintenance of the node can be done (upgrading, rebooting, etc).
     */
    void suspendNode(NodeAgentContext context);

    void restartVespa(NodeAgentContext context);

    void startServices(NodeAgentContext context);

    void stopServices(NodeAgentContext context);

    Optional<ContainerStats> getContainerStats(NodeAgentContext context);

    boolean noManagedContainersRunning();

    /** Deletes the local images that are currently not in use by any container and not recently used. */
    boolean deleteUnusedDockerImages(List<DockerImage> excludes, Duration minImageAgeToDelete);
}
