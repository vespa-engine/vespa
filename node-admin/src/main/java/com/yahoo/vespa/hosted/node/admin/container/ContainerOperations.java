// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.container.image.ContainerImageDownloader;
import com.yahoo.vespa.hosted.node.admin.container.image.ContainerImagePruner;
import com.yahoo.vespa.hosted.node.admin.nodeagent.ContainerData;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.nio.file.FileSystem;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * High-level interface for container operations. Code managing containers should use this and not
 * {@link ContainerEngine} directly.
 *
 * @author hakonhall
 * @author mpolden
 */
public class ContainerOperations {

    private final ContainerEngine containerEngine;
    private final ContainerImageDownloader imageDownloader;
    private final ContainerImagePruner imagePruner;
    private final ContainerStatsCollector containerStatsCollector;

    public ContainerOperations(ContainerEngine containerEngine, CGroup cgroup, FileSystem fileSystem) {
        this.containerEngine = Objects.requireNonNull(containerEngine);
        this.imageDownloader = new ContainerImageDownloader(containerEngine);
        this.imagePruner = new ContainerImagePruner(containerEngine, Clock.systemUTC());
        this.containerStatsCollector = new ContainerStatsCollector(containerEngine, cgroup, fileSystem);
    }

    public ContainerData createContainer(NodeAgentContext context, ContainerResources containerResources) {
        return containerEngine.createContainer(context, containerResources);
    }

    public void startContainer(NodeAgentContext context) {
        containerEngine.startContainer(context);
    }

    public void removeContainer(NodeAgentContext context, Container container) {
        containerEngine.removeContainer(context, container);
    }

    public void updateContainer(NodeAgentContext context, ContainerId containerId, ContainerResources containerResources) {
        containerEngine.updateContainer(context, containerId, containerResources);
    }

    public Optional<Container> getContainer(NodeAgentContext context) {
        return containerEngine.getContainer(context);
    }

    /** Pull image asynchronously. Returns true if image is still downloading and false if download is complete */
    public boolean pullImageAsyncIfNeeded(TaskContext context, DockerImage dockerImage, RegistryCredentials registryCredentials) {
        return !imageDownloader.get(context, dockerImage, registryCredentials);
    }

    /** Executes a command inside container identified by given context. Does NOT throw on non-zero exit code */
    public CommandResult executeCommandInContainer(NodeAgentContext context, UnixUser user, String... command) {
        return executeCommandInContainer(context, user, CommandLine.DEFAULT_TIMEOUT, command);
    }

    /** Execute command inside container identified by given context. Does NOT throw on non-zero exit code */
    public CommandResult executeCommandInContainer(NodeAgentContext context, UnixUser user, Duration timeout, String... command) {
        return containerEngine.execute(context, user, timeout, command);
    }

    /** Execute command in inside containers network namespace, identified by given context. Throws on non-zero exit code */
    public CommandResult executeCommandInNetworkNamespace(NodeAgentContext context, String... command) {
        return containerEngine.executeInNetworkNamespace(context, command);
    }

    /** Resume node. Resuming a node means that it is ready to receive traffic */
    public String resumeNode(NodeAgentContext context) {
        return executeNodeCtlInContainer(context, "resume");
    }

    /**
     * Suspend node and return output. Suspending a node means the node should be taken temporarily offline,
     * such that maintenance of the node can be done (upgrading, rebooting, etc).
     */
    public String suspendNode(NodeAgentContext context) {
        return executeNodeCtlInContainer(context, "suspend");
    }

    /** Restart Vespa inside container. Same as running suspend, stop, start and resume */
    public String restartVespa(NodeAgentContext context) {
        return executeNodeCtlInContainer(context, "restart-vespa");
    }

    /** Start Vespa inside container */
    public String startServices(NodeAgentContext context) {
        return executeNodeCtlInContainer(context, "start");
    }

    /** Stop Vespa inside container */
    public String stopServices(NodeAgentContext context) {
        return executeNodeCtlInContainer(context, "stop");
    }

    /** Get container statistics */
    public Optional<ContainerStats> getContainerStats(NodeAgentContext context) {
        String iface = containerEngine.networkInterface(context);
        return getContainer(context).flatMap(container -> containerStatsCollector.collect(context, container.id(), container.pid(), iface));
    }

    /** Returns true if no containers managed by node-admin are running */
    public boolean noManagedContainersRunning(TaskContext context) {
        return containerEngine.listContainers(context).stream()
                              .filter(PartialContainer::managed)
                              .noneMatch(container -> container.state() == Container.State.running);
    }

    /**
     * Stop and remove all managed containers except the given ones
     *
     * @return true if any containers were removed
     */
    public boolean retainManagedContainers(TaskContext context, Set<ContainerName> containerNames) {
        return containerEngine.listContainers(context).stream()
                              .filter(PartialContainer::managed)
                              .filter(container -> !containerNames.contains(container.name()))
                              .peek(container -> containerEngine.removeContainer(context, container))
                              .count() > 0;
    }

    /** Deletes the local images that are currently not in use by any container and not recently used. */
    public boolean deleteUnusedContainerImages(TaskContext context, List<DockerImage> excludes, Duration minImageAgeToDelete) {
        List<String> excludedRefs = excludes.stream().map(DockerImage::asString).toList();
        return imagePruner.removeUnusedImages(context, excludedRefs, minImageAgeToDelete);
    }

    private String executeNodeCtlInContainer(NodeAgentContext context, String program) {
        String[] command = new String[] {context.paths().underVespaHome("bin/vespa-nodectl").pathInContainer(), program};
        return executeCommandInContainer(context, context.users().vespa(), command).getOutput();
    }

}
