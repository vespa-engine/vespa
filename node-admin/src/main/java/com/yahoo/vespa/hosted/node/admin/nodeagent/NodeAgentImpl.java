// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.CONTAINER_ABSENT;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.RUNNING_NODE;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.RUNNING_NODE_HOWEVER_RESUME_SCRIPT_NOT_RUN;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author dybis
 * @author bakksjo
 */
public class NodeAgentImpl implements NodeAgent {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private AtomicBoolean frozen = new AtomicBoolean(false);

    private static final Logger logger = Logger.getLogger(NodeAgentImpl.class.getName());

    private DockerImage imageBeingDownloaded = null;

    private final String logPrefix;
    private final HostName hostname;

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final NodeDocker nodeDocker;

    private final Object monitor = new Object();
    @GuardedBy("monitor")
    private State state = State.WAITING;


    public enum ContainerState {
        CONTAINER_ABSENT,
        RUNNING_NODE_HOWEVER_RESUME_SCRIPT_NOT_RUN,
        RUNNING_NODE
    }
    ContainerState containerState = CONTAINER_ABSENT;

    // The attributes of the last successful noderepo attribute update for this node. Used to avoid redundant calls.
    private NodeAttributes lastAttributesSet = null;

    public NodeAgentImpl(
            final HostName hostName,
            final NodeRepository nodeRepository,
            final Orchestrator orchestrator,
            final NodeDocker nodeDocker) {
        this.logPrefix = "NodeAgent(" + hostName + "): ";
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.hostname = hostName;
        this.nodeDocker = nodeDocker;
    }

    @Override
    public void execute(Command command, boolean blocking) {
        switch (command) {
            case UPDATE_FROM_NODE_REPO:
                break;
            case SET_FREEZE:
                frozen.set(true);
                break;
            case UNFREEZE:
                frozen.set(false);
                break;
            default:
                throw new RuntimeException("Unknown command " + command.name());
        }
        if (blocking) {
            nodeTick();
        } else {
            nodeTickInNewThread();
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
        if (scheduler.isTerminated()) {
            throw new RuntimeException("Can not restart a node agent.");
        }
        scheduler.scheduleWithFixedDelay(this::nodeTick, 1, 60, SECONDS);
    }

    @Override
    public void stop() {
        if (scheduler.isTerminated()) {
            throw new RuntimeException("Can not re-stop a node agent.");
        }
        scheduler.shutdown();
        synchronized (monitor) {
            state = State.TERMINATED;
        }
    }

    private boolean isFrozen() {
        synchronized (monitor) {
            if (state == State.TERMINATED) {
                return true;
            }
            if (frozen.get()) {
                state = State.FROZEN;
                return true;
            }
            state = State.WORKING;
        }
        return false;
    }

    public void startNodeInContainerIfNeeded(final ContainerNodeSpec nodeSpec) {
        if (containerState != RUNNING_NODE_HOWEVER_RESUME_SCRIPT_NOT_RUN) {
            return;
        }
        logger.log(Level.INFO, logPrefix + "Starting optional node program resume command");
        nodeDocker.executeResume(nodeSpec.containerName);//, RESUME_NODE_COMMAND);
        containerState = RUNNING_NODE;
    }

    public void publishThatNodeIsRunningIfRequired(final ContainerNodeSpec nodeSpec) throws IOException {
        final String containerVespaVersion = nodeDocker.getVespaVersionOrNull(nodeSpec.containerName);

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
    }

    public void startContainerIfNeeded(final ContainerNodeSpec nodeSpec) {
        if (nodeDocker.startContainerIfNeeded(nodeSpec)) {
            containerState = RUNNING_NODE_HOWEVER_RESUME_SCRIPT_NOT_RUN;
        } else {
            // In case container was already running on startup, we found the container, but should call
            if (containerState == CONTAINER_ABSENT) {
                containerState = RUNNING_NODE_HOWEVER_RESUME_SCRIPT_NOT_RUN;
            }
        }
    }

    private void nodeTickInNewThread() {
        new Thread(this::nodeTick).start();
    }

    private void removeContainerIfNeededUpdateContainerState(ContainerNodeSpec nodeSpec) throws Exception {
        if (nodeDocker.removeContainerIfNeeded(nodeSpec, hostname, orchestrator)) {
            containerState = CONTAINER_ABSENT;
        }
    }

    private boolean scheduleDownLoadIfNeededIsImageReady(ContainerNodeSpec nodeSpec) {
        if (nodeDocker.shouldScheduleDownloadOfImage(nodeSpec.wantedDockerImage.get())) {
            if (imageBeingDownloaded == nodeSpec.wantedDockerImage.get()) {
                // Downloading already scheduled, but not done.
                return false;
            }
            imageBeingDownloaded = nodeSpec.wantedDockerImage.get();
            // Create a tick when download is finished.
            nodeDocker.scheduleDownloadOfImage(nodeSpec, this::nodeTickInNewThread);
            return false;
        } else {
            imageBeingDownloaded = null;
            return true;
        }
    }


    private void nodeTick() {
        if (isFrozen()) {
            return;
        }
        synchronized (monitor) {
            try {
                final ContainerNodeSpec nodeSpec = nodeRepository.getContainerNodeSpec(hostname)
                        .orElseThrow(() ->
                                new IllegalStateException(String.format("Node '%s' missing from node repository.", hostname)));

                switch (nodeSpec.nodeState) {
                    case PROVISIONED:
                        removeContainerIfNeededUpdateContainerState(nodeSpec);
                        logger.log(LogLevel.INFO, logPrefix + "State is provisioned, will delete application storage and mark node as ready");
                        nodeDocker.deleteContainerStorage(nodeSpec.containerName);
                        nodeRepository.markAsReady(nodeSpec.hostname);
                        break;
                    case READY:
                        removeContainerIfNeededUpdateContainerState(nodeSpec);
                        break;
                    case RESERVED:
                        removeContainerIfNeededUpdateContainerState(nodeSpec);
                        break;
                    case ACTIVE:
                        if (! scheduleDownLoadIfNeededIsImageReady(nodeSpec)) {
                            return;
                        }
                        removeContainerIfNeededUpdateContainerState(nodeSpec);

                        startContainerIfNeeded(nodeSpec);
                        startNodeInContainerIfNeeded(nodeSpec);
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
                        publishThatNodeIsRunningIfRequired(nodeSpec);
                        break;
                    case INACTIVE:
                        removeContainerIfNeededUpdateContainerState(nodeSpec);
                        break;
                    case DIRTY:
                        removeContainerIfNeededUpdateContainerState(nodeSpec);
                        logger.log(LogLevel.INFO, logPrefix + "State is dirty, will delete application storage and mark node as ready");
                        nodeDocker.deleteContainerStorage(nodeSpec.containerName);
                        nodeRepository.markAsReady(nodeSpec.hostname);
                        break;
                    case FAILED:
                        removeContainerIfNeededUpdateContainerState(nodeSpec);
                        break;
                }
            } catch (Exception e) {
                logger.log(LogLevel.ERROR, logPrefix + "Unhandled exception, ignoring.", e);
            } catch (Throwable t) {
                logger.log(LogLevel.ERROR, logPrefix + "Unhandled throwable, taking down system.", t);
                System.exit(234);
            }
        }
    }
}
