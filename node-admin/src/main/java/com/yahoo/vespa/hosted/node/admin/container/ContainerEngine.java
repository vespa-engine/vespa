// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.container.image.Image;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Interface for a container engine, such as Docker or Podman.
 *
 * @author mpolden
 */
public interface ContainerEngine {

    /** Create a new container */
    void createContainer(NodeAgentContext context, ContainerData containerData, ContainerResources containerResources);

    /** Start a created container */
    void startContainer(NodeAgentContext context);

    /** Update an existing container */
    void updateContainer(NodeAgentContext context, ContainerId containerId, ContainerResources containerResources);

    /** Remove given container. The container will be stopped if necessary */
    void removeContainer(TaskContext context, PartialContainer container);

    /** Get container for given context */
    Optional<Container> getContainer(NodeAgentContext context);

    /** List containers managed by this */
    List<PartialContainer> listContainers(TaskContext context);

    /** Returns the network interface used by container in given context */
    String networkInterface(NodeAgentContext context);

    /** Executes a command in inside container as root user, throws on non-zero exit code */
    CommandResult executeAsRoot(NodeAgentContext context, Duration timeout, String... command);

    /** Executes a command in inside containers network namespace, throws on non-zero exit code */
    CommandResult executeInNetworkNamespace(NodeAgentContext context, String... command);

    /** Download giving image */
    void pullImage(TaskContext context, DockerImage image, RegistryCredentials registryCredentials);

    /** Returns whether given image is already downloaded */
    boolean hasImage(TaskContext context, DockerImage image);

    /** Remove given image */
    void removeImage(TaskContext context, String id);

    /** Returns images available in this */
    List<Image> listImages(TaskContext context);

}
