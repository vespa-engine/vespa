// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.node.admin.docker.Container;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.docker.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;

import javax.annotation.concurrent.GuardedBy;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bakksjo
 */
public class NodeAgentImpl implements NodeAgent {
    private static final Logger logger = Logger.getLogger(NodeAgentImpl.class.getName());
    static final String NODE_PROGRAM = Defaults.getDefaults().vespaHome() + "bin/vespa-nodectl";
    private static final String[] RESUME_NODE_COMMAND = new String[] {NODE_PROGRAM, "resume"};
    private static final String[] SUSPEND_NODE_COMMAND = new String[] {NODE_PROGRAM, "suspend"};

    private final String logPrefix;
    private final HostName hostname;

    private final Docker docker;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;

    private final Thread thread;

    private final Object monitor = new Object();
    @GuardedBy("monitor")
    private State state = State.WAITING;
    @GuardedBy("monitor")
    private State wantedState = State.WAITING;

    // The attributes of the last successful noderepo attribute update for this node. Used to avoid redundant calls.
    // Only used internally by maintenance thread; no synchronization necessary.
    private NodeAttributes lastAttributesSet = null;
    // Whether we have successfully started the node using the node program. Used to avoid redundant start calls.
    private boolean nodeStarted = false;




    /**
     * @param hostName the hostname of the node managed by this agent
     * @param docker interface to docker daemon and docker-related tasks
     * @param nodeRepository interface to (remote) node repository
     * @param orchestrator interface to (remote) orchestrator
     */
    public NodeAgentImpl(
            final HostName hostName,
            final Docker docker,
            final NodeRepository nodeRepository,
            final Orchestrator orchestrator) {
        this.logPrefix = "NodeAgent(" + hostName + "): ";
        this.docker = docker;
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.thread = new Thread(this::maintainWantedState, "Node Agent maintenance thread for node " + hostName);
        this.hostname = hostName;
    }

    @Override
    public void execute(Command command) {
        synchronized (monitor) {
            switch (command) {
                case UPDATE_FROM_NODE_REPO:
                    wantedState = State.WORKING;
                    break;
                case FREEZE:
                    wantedState = State.FROZEN;
                    break;
                case UNFREEZE:
                    wantedState = State.WORKING;
                    break;
            }
        }
    }

    @Override
    public State getState() {
        synchronized (monitor) {
            return state;
        }
    }

    @Override
    public void start() {
        logger.log(LogLevel.INFO, logPrefix + "Scheduling start of NodeAgent");
        synchronized (monitor) {
            if (state == State.TERMINATED) {
                throw new IllegalStateException("Cannot re-start a stopped node agent");
            }
        }
        thread.start();
    }

    @Override
    public void terminate() {
        logger.log(LogLevel.INFO, logPrefix + "Scheduling stop of NodeAgent");
        synchronized (monitor) {
            if (state == State.TERMINATED) {
                throw new IllegalStateException("Cannot stop an already stopped node agent");
            }
            wantedState = State.TERMINATED;
        }
        monitor.notifyAll();
        try {
            thread.join();
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, logPrefix + "Unexpected interrupt", e);
        }
    }

    void synchronizeLocalContainerState(
            final ContainerNodeSpec nodeSpec,
            Optional<Container> existingContainer) throws Exception {
        logger.log(Level.INFO, logPrefix + "Container " + nodeSpec.containerName + " state:" + nodeSpec.nodeState);

        if (nodeSpec.nodeState == NodeState.ACTIVE && !docker.imageIsDownloaded(nodeSpec.wantedDockerImage.get())) {
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
                scheduleWork();
            });

            return;
        }

        if (existingContainer.isPresent()) {
            Optional<String> removeReason = Optional.empty();
            if (nodeSpec.nodeState != NodeState.ACTIVE) {
                removeReason = Optional.of("Node no longer active");
            } else if (!nodeSpec.wantedDockerImage.get().equals(existingContainer.get().image)) {
                removeReason = Optional.of("The node is supposed to run a new Docker image: "
                        + existingContainer.get() + " -> " + nodeSpec.wantedDockerImage.get());
            } else if (nodeSpec.currentRestartGeneration.get() < nodeSpec.wantedRestartGeneration.get()) {
                removeReason = Optional.of("Restart requested - wanted restart generation has been bumped: "
                        + nodeSpec.currentRestartGeneration.get() + " -> " + nodeSpec.wantedRestartGeneration.get());
            } else if (!existingContainer.get().isRunning) {
                removeReason = Optional.of("Container no longer running");
            }

            if (removeReason.isPresent()) {
                logger.log(LogLevel.INFO, logPrefix + "Will remove container " + existingContainer.get() + ": "
                        + removeReason.get());
                removeContainer(nodeSpec, existingContainer.get());
                existingContainer = Optional.empty(); // Make logic below easier
            }
        }

        switch (nodeSpec.nodeState) {
            case DIRTY: // intentional fall-through
            case PROVISIONED:
                logger.log(LogLevel.INFO, logPrefix + "State is " + nodeSpec.nodeState
                        + ", will delete application storage and mark node as ready");
                docker.deleteApplicationStorage(nodeSpec.containerName);
                nodeRepository.markAsReady(nodeSpec.hostname);
                break;
            case ACTIVE:
                if (!existingContainer.isPresent()) {
                    logger.log(Level.INFO, logPrefix + "Starting container " + nodeSpec.containerName);
                    // TODO: Properly handle absent min* values
                    docker.startContainer(
                            nodeSpec.wantedDockerImage.get(),
                            nodeSpec.hostname,
                            nodeSpec.containerName,
                            nodeSpec.minCpuCores.get(),
                            nodeSpec.minDiskAvailableGb.get(),
                            nodeSpec.minMainMemoryAvailableGb.get());
                    nodeStarted = false;
                }

                if (!nodeStarted) {
                    logger.log(Level.INFO, logPrefix + "Starting optional node program " + RESUME_NODE_COMMAND);
                    Optional<ProcessResult> result = executeOptionalProgram(docker, nodeSpec.containerName, RESUME_NODE_COMMAND);

                    if (result.isPresent() && !result.get().isSuccess()) {
                        throw new RuntimeException("Container " + nodeSpec.containerName.asString()
                                + ": Command " + Arrays.toString(RESUME_NODE_COMMAND) + " failed: " + result.get());
                    }

                    nodeStarted = true;
                }

                final String containerVespaVersion = nullOnException(() ->
                        docker.getVespaVersion(nodeSpec.containerName));

                // Because it's more important to stop a bad release from rolling out in prod,
                // we put the resume call last. So if we fail after updating the node repo attributes
                // but before resume, the app may go through the tenant pipeline but will halt in prod.
                //
                // Note that this problem exists only because there are 2 different mechanisms
                // that should really be parts of a single mechanism:
                //  - The content of node repo is used to determine whether a new Vespa+application
                //    has been successfully rolled out.
                //  - Slobrok and internal orchestrator state is used to determine whether
                //    to allow upgrade (suspend).

                final NodeAttributes currentAttributes = new NodeAttributes(
                        nodeSpec.wantedRestartGeneration.get(),
                        nodeSpec.wantedDockerImage.get(),
                        containerVespaVersion);
                if (!currentAttributes.equals(lastAttributesSet)) {
                    logger.log(Level.INFO, logPrefix + "Publishing new set of attributes to node repo: "
                            + lastAttributesSet + " -> " + currentAttributes);
                    nodeRepository.updateNodeAttributes(
                            nodeSpec.hostname,
                            currentAttributes.restartGeneration,
                            currentAttributes.dockerImage,
                            currentAttributes.vespaVersion);
                    lastAttributesSet = currentAttributes;
                }

                logger.log(Level.INFO, logPrefix + "Call resume against Orchestrator");
                orchestrator.resume(nodeSpec.hostname);
                break;
            default:
                // Nothing to do...
        }
    }

    private void removeContainer(final ContainerNodeSpec nodeSpec, final Container existingContainer)
            throws Exception {
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

    static String[] programExistsCommand(String programPath) {
        return new String[]{ "/usr/bin/env", "test", "-x", programPath };
    }

    /**
     * Executes a program and returns its result, or if it doesn't exist, return a result
     * as-if the program executed with exit status 0 and no output.
     */
    static Optional<ProcessResult> executeOptionalProgram(Docker docker, ContainerName containerName, String... args) {
        assert args.length > 0;
        String[] nodeProgramExistsCommand = programExistsCommand(args[0]);
        if (!docker.executeInContainer(containerName, nodeProgramExistsCommand).isSuccess()) {
            return Optional.empty();
        }

        return Optional.of(docker.executeInContainer(containerName, args));
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
            result = executeOptionalProgram(docker, containerName, SUSPEND_NODE_COMMAND);
        } catch (RuntimeException e) {
            // It's bad to continue as-if nothing happened, but on the other hand if we do not proceed to
            // remove container, we will not be able to upgrade to fix any problems in the suspend logic!
            logger.log(LogLevel.WARNING, logPrefix + "Failed trying to suspend node with "
                    + Arrays.toString(SUSPEND_NODE_COMMAND), e);
            return;
        }

        if (result.isPresent() && !result.get().isSuccess()) {
            logger.log(LogLevel.WARNING, logPrefix + "The suspend program " + Arrays.toString(SUSPEND_NODE_COMMAND)
                    + " failed: " + result.get().getOutput());
        }
    }

    private static <T> T nullOnException(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Ignoring failure", e);
            return null;
        }
    }

    // It somewhat sucks that this class almost duplicates a binding class used by NodeRepositoryImpl,
    // but using the binding class here would be a layer violation, and would also tie this logic to
    // serialization-related dependencies it needs not have.
    private static class NodeAttributes {
        private final long restartGeneration;
        private final DockerImage dockerImage;
        private final String vespaVersion;

        private NodeAttributes(long restartGeneration, DockerImage dockerImage, String vespaVersion) {
            this.restartGeneration = restartGeneration;
            this.dockerImage = dockerImage;
            this.vespaVersion = vespaVersion;
        }

        @Override
        public int hashCode() {
            return Objects.hash(restartGeneration, dockerImage, vespaVersion);
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof NodeAttributes)) {
                return false;
            }
            final NodeAttributes other = (NodeAttributes) o;

            return Objects.equals(restartGeneration, other.restartGeneration)
                    && Objects.equals(dockerImage, other.dockerImage)
                    && Objects.equals(vespaVersion, other.vespaVersion);
        }

        @Override
        public String toString() {
            return "NodeAttributes{" +
                    "restartGeneration=" + restartGeneration +
                    ", dockerImage=" + dockerImage +
                    ", vespaVersion='" + vespaVersion + '\'' +
                    '}';
        }
    }

    private void scheduleWork() {
        synchronized (monitor) {
            if (wantedState != State.FROZEN) {
                wantedState = State.WORKING;
            } else {
                logger.log(Level.FINE, "Not scheduling work since in freeze.");
            }
        }
        monitor.notifyAll();
    }

    private void blockUntilNotWaitingOrFrozen() {
            try {
                synchronized (monitor) {
                    while (wantedState == State.WAITING || wantedState == State.FROZEN) {
                        state = wantedState;
                        monitor.wait();
                        continue;
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
    }

    private void maintainWantedState()  {
        while (true) {
            blockUntilNotWaitingOrFrozen();
            synchronized (monitor) {
                switch (wantedState) {
                    case WAITING:
                        state = State.WAITING;
                        continue;
                    case WORKING:
                        state = State.WORKING;
                        break;
                    case FROZEN:
                        state = State.FROZEN;
                        continue;
                    case TERMINATED:
                        return;
                }
            }
            // This is WORKING state.
            try {
                final ContainerNodeSpec nodeSpec = nodeRepository.getContainerNodeSpec(hostname)
                        .orElseThrow(() ->
                                new IllegalStateException(String.format("Node '%s' missing from node repository.", hostname)));
                final Optional<Container> existingContainer = docker.getContainer(hostname);
                synchronizeLocalContainerState(nodeSpec, existingContainer);
            } catch (Exception e) {
                logger.log(LogLevel.ERROR, logPrefix + "Unhandled exception.", e);
            }
        }
    }
}
