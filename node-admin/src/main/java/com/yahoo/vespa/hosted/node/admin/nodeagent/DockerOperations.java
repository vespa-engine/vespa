// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.Container;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that wraps the Docker class and have some tools related to running programs in docker.
 * @author dybis
 */
public class DockerOperations {
    static final String NODE_PROGRAM = Defaults.getDefaults().vespaHome() + "bin/vespa-nodectl";

    private static final String[] RESUME_NODE_COMMAND = new String[] {NODE_PROGRAM, "resume"};
    private static final String[] SUSPEND_NODE_COMMAND = new String[] {NODE_PROGRAM, "suspend"};

    private static final Logger logger = Logger.getLogger(DockerOperations.class.getName());
    private final Docker docker;

    public DockerOperations(Docker docker) {
        this.docker = docker;
    }

    // Returns null on problems
    public String getVespaVersionOrNull(ContainerName containerName) {
        try {
        return docker.getVespaVersion(containerName);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Ignoring failure", e);
            return null;
        }
    }

    // Returns true if started
    public boolean startContainerIfNeeded(final ContainerNodeSpec nodeSpec) {
        final Optional<Container> existingContainer = docker.getContainer(nodeSpec.hostname);
        if (!existingContainer.isPresent()) {
            startContainer(nodeSpec);
            return true;
        } else {
            return false;
        }
    }

    // Returns true if scheduling download
    public boolean shouldScheduleDownloadOfImage(final DockerImage dockerImage) {
        return !docker.imageIsDownloaded(dockerImage);
    }

    public boolean removeContainerIfNeeded(ContainerNodeSpec nodeSpec, HostName hostname, Orchestrator orchestrator)
            throws Exception {
        Optional<Container> existingContainer = docker.getContainer(hostname);
        if (! existingContainer.isPresent()) {
            return true;
        }
        Optional<String> removeReason = shouldRemoveContainer(nodeSpec, existingContainer);
        if (removeReason.isPresent()) {
            logger.log(LogLevel.INFO, "NodeAgent(" + hostname + "): " + "Will remove container " + existingContainer.get() + ": "
                    + removeReason.get());
            removeContainer(nodeSpec, existingContainer.get(), orchestrator);
            return true;
        }
        return false;
    }

    public void deleteContainerStorage(ContainerName containerName) throws IOException {
        docker.deleteApplicationStorage(containerName);
    }

    private Optional<String> shouldRemoveContainer(ContainerNodeSpec nodeSpec, Optional<Container> existingContainer) {
        if (nodeSpec.nodeState != NodeState.ACTIVE) {
            return Optional.of("Node no longer active");
        }
        if (!nodeSpec.wantedDockerImage.get().equals(existingContainer.get().image)) {
            return Optional.of("The node is supposed to run a new Docker image: "
                    + existingContainer.get() + " -> " + nodeSpec.wantedDockerImage.get());
        }
        if (nodeSpec.currentRestartGeneration.get() < nodeSpec.wantedRestartGeneration.get()) {
            return Optional.of("Restart requested - wanted restart generation has been bumped: "
                    + nodeSpec.currentRestartGeneration.get() + " -> " + nodeSpec.wantedRestartGeneration.get());
        }
        if (!existingContainer.get().isRunning) {
            return Optional.of("Container no longer running");
        }
        return Optional.empty();
    }

    /**
     * Executes a program and returns its result, or if it doesn't exist, return a result
     * as-if the program executed with exit status 0 and no output.
     */
    Optional<ProcessResult> executeOptionalProgram(ContainerName containerName, String... args) {
        assert args.length > 0;
        String[] nodeProgramExistsCommand = programExistsCommand(args[0]);
        if (!docker.executeInContainer(containerName, nodeProgramExistsCommand).isSuccess()) {
            return Optional.empty();
        }

        return Optional.of(docker.executeInContainer(containerName, args));
    }

    String[] programExistsCommand(String programPath) {
        return new String[]{ "/usr/bin/env", "test", "-x", programPath };
    }

    /**
     * Try to suspend node. Suspending a node means the node should be taken offline,
     * such that maintenance can be done of the node (upgrading, rebooting, etc),
     * and such that we will start serving again as soon as possible afterwards.
     *
     * Any failures are logged and ignored.
     */
    private void trySuspendNode(ContainerName containerName) {

        Optional<ProcessResult> result;

        try {
            // TODO: Change to waiting w/o timeout (need separate thread that we can stop).
            result = executeOptionalProgram(containerName, SUSPEND_NODE_COMMAND);
        } catch (RuntimeException e) {
            // It's bad to continue as-if nothing happened, but on the other hand if we do not proceed to
            // remove container, we will not be able to upgrade to fix any problems in the suspend logic!
            logger.log(LogLevel.WARNING,  "Failed trying to suspend container " + containerName.asString() + "  with "
                   + Arrays.toString(SUSPEND_NODE_COMMAND), e);
            return;
        }

        if (result.isPresent() && !result.get().isSuccess()) {

            logger.log(LogLevel.WARNING, "The suspend program " + Arrays.toString(SUSPEND_NODE_COMMAND)
                    + " failed: " + result.get().getOutput() + " for container " + containerName.asString());
        }
    }

    void startContainer(final ContainerNodeSpec nodeSpec) {
        String logPrefix = "NodeAgent(" + nodeSpec.hostname+ "): ";

        logger.log(Level.INFO, logPrefix + "Starting container " + nodeSpec.containerName);
        // TODO: Properly handle absent min* values
        docker.startContainer(
                nodeSpec.wantedDockerImage.get(),
                nodeSpec.hostname,
                nodeSpec.containerName,
                nodeSpec.minCpuCores.get(),
                nodeSpec.minDiskAvailableGb.get(),
                nodeSpec.minMainMemoryAvailableGb.get());
    }

    void scheduleDownloadOfImage(final ContainerNodeSpec nodeSpec, Runnable callback) {
        String logPrefix = "NodeAgent(" + nodeSpec.hostname+ "): ";

        logger.log(LogLevel.INFO, logPrefix + "Schedule async download of Docker image " + nodeSpec.wantedDockerImage.get());
        final CompletableFuture<DockerImage> asyncPullResult = docker.pullImageAsync(nodeSpec.wantedDockerImage.get());
        asyncPullResult.whenComplete((dockerImage, throwable) -> {
            if (throwable != null) {
                logger.log(
                        Level.WARNING,
                        logPrefix + "Failed to pull docker image " + nodeSpec.wantedDockerImage,
                        throwable);
                return;
            }
            assert nodeSpec.wantedDockerImage.get().equals(dockerImage);
            callback.run();
        });

        return;
    }

    private void removeContainer(final ContainerNodeSpec nodeSpec, final Container existingContainer, Orchestrator orchestrator)
            throws Exception {
        String logPrefix = "NodeAgent(" + nodeSpec.hostname+ "): ";
        final ContainerName containerName = existingContainer.name;
        if (existingContainer.isRunning) {
            // If we're stopping the node only to upgrade or restart the node or similar, we need to suspend
            // the services.
            if (nodeSpec.nodeState == NodeState.ACTIVE) {
                // TODO: Also skip orchestration if we're downgrading in test/staging
                // How to implement:
                //  - test/staging: We need to figure out whether we're in test/staging, by asking Chef!? Or,
                //    let the Orchestrator handle it - it may know what zone we're in.
                //  - downgrading: Impossible to know unless we look at the hosted version, which is
                //    not available in the docker image (nor its name). Not sure how to solve this. Should
                //    the node repo return the hosted version or a downgrade bit in addition to
                //    wanted docker image etc?
                // Should the tenant pipeline instead use BCP tool to upgrade faster!?
                //
                // More generally, the node repo response should contain sufficient info on what the docker image is,
                // to allow the node admin to make decisions that depend on the docker image. Or, each docker image
                // needs to contain routines for drain and suspend. For many image, these can just be dummy routines.

                logger.log(Level.INFO, logPrefix + "Ask Orchestrator for permission to suspend node " + nodeSpec.hostname);
                final boolean suspendAllowed = orchestrator.suspend(nodeSpec.hostname);
                if (!suspendAllowed) {
                    logger.log(Level.INFO, logPrefix + "Orchestrator rejected suspend of node");
                    // TODO: change suspend() to throw an exception if suspend is denied
                    throw new OrchestratorException("Failed to get permission to suspend " + nodeSpec.hostname);
                }

                trySuspendNode(containerName);
            }

            logger.log(Level.INFO, logPrefix + "Stopping container " + containerName);
            docker.stopContainer(containerName);
        }

        logger.log(Level.INFO, logPrefix + "Deleting container " + containerName);
        docker.deleteContainer(containerName);
    }


    public void executeResume(ContainerName containerName) {
        Optional<ProcessResult> result = executeOptionalProgram(containerName, RESUME_NODE_COMMAND);

        if (result.isPresent() && !result.get().isSuccess()) {
            throw new RuntimeException("Container " +containerName.asString()
                    + ": command " + RESUME_NODE_COMMAND + " failed: " + result.get());
        }
    }
}
