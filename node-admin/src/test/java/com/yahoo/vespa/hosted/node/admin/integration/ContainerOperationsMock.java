// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integration;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerId;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ContainerResources;
import com.yahoo.vespa.hosted.dockerapi.ContainerStats;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.dockerapi.RegistryCredentials;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mock with some simple logic
 *
 * @author freva
 */
public class ContainerOperationsMock implements ContainerOperations {

    public static final ContainerId CONTAINER_ID = new ContainerId("af345");

    private final Map<ContainerName, Container> containersByContainerName = new HashMap<>();
    private final Object monitor = new Object();

    @Override
    public void createContainer(NodeAgentContext context, ContainerData containerData, ContainerResources containerResources) {
        synchronized (monitor) {
            containersByContainerName.put(context.containerName(),
                                          new Container(CONTAINER_ID,
                                                        context.hostname().value(),
                                                        context.node().wantedDockerImage().get(),
                                                        containerResources,
                                                        context.containerName(),
                                                        Container.State.CREATED,
                                                        2));
        }
    }

    @Override
    public void startContainer(NodeAgentContext context) {
        synchronized (monitor) {
            Optional<Container> container = getContainer(context);
            if (container.isEmpty())  throw new IllegalArgumentException("Cannot start non-existent container " + context.containerName());
            containersByContainerName.put(context.containerName(), new Container(CONTAINER_ID,
                                                                                 container.get().hostname,
                                                                                 container.get().image,
                                                                                 container.get().resources,
                                                                                 container.get().name,
                                                                                 Container.State.RUNNING,
                                                                                 container.get().pid));
        }
    }

    @Override
    public void removeContainer(NodeAgentContext context, Container container) {
        synchronized (monitor) {
            containersByContainerName.remove(container.name);
        }
    }

    @Override
    public void updateContainer(NodeAgentContext context, ContainerId containerId, ContainerResources containerResources) {
        synchronized (monitor) {
            Container container = containersByContainerName.get(context.containerName());
            containersByContainerName.put(context.containerName(),
                                          new Container(container.id(), container.hostname, container.image,
                                                        containerResources, container.name, container.state, container.pid));
        }
    }

    @Override
    public Optional<Container> getContainer(NodeAgentContext context) {
        synchronized (monitor) {
            return Optional.ofNullable(containersByContainerName.get(context.containerName()));
        }
    }

    @Override
    public boolean pullImageAsyncIfNeeded(TaskContext context, DockerImage dockerImage, RegistryCredentials registryCredentials) {
        return false;
    }

    @Override
    public ProcessResult executeCommandInContainerAsRoot(NodeAgentContext context, String... command) {
        return null;
    }

    @Override
    public ProcessResult executeCommandInContainerAsRoot(NodeAgentContext context, Long timeoutSeconds, String... command) {
        return null;
    }

    @Override
    public CommandResult executeCommandInNetworkNamespace(NodeAgentContext context, String... command) {
        return null;
    }

    @Override
    public String resumeNode(NodeAgentContext context) {
        return "";
    }

    @Override
    public String suspendNode(NodeAgentContext context) {
        return "";
    }

    @Override
    public String restartVespa(NodeAgentContext context) {
        return "";
    }

    @Override
    public String startServices(NodeAgentContext context) {
        return "";
    }

    @Override
    public String stopServices(NodeAgentContext context) {
        return "";
    }

    @Override
    public Optional<ContainerStats> getContainerStats(NodeAgentContext context) {
        return Optional.empty();
    }

    @Override
    public boolean noManagedContainersRunning(TaskContext context) {
        return false;
    }

    @Override
    public boolean retainManagedContainers(TaskContext context, Set<ContainerName> containerNames) {
        return false;
    }

    @Override
    public boolean deleteUnusedContainerImages(TaskContext context, List<DockerImage> excludes, Duration minImageAgeToDelete) {
        return false;
    }

}
