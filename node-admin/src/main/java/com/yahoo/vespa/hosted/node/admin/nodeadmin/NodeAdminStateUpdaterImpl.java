// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.config.provision.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.hosted.provision.Node;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN;
import static com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater.State.TRANSITIONING;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdaterImpl implements NodeAdminStateUpdater {
    static final Duration FREEZE_CONVERGENCE_TIMEOUT = Duration.ofMinutes(5);
    static final String TRANSITION_EXCEPTION_MESSAGE = "NodeAdminStateUpdater has not run since current wanted state was set";

    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private State currentState = SUSPENDED_NODE_ADMIN;
    private State wantedState = RESUMED;
    private boolean workToDoNow = true;

    private final Object monitor = new Object();
    private RuntimeException lastConvergenceException;

    private final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());
    private final Thread loopThread;

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final NodeAdmin nodeAdmin;
    private final Clock clock;
    private final String hostHostname;
    private final Duration nodeAdminConvergeStateInterval;

    private Instant lastTick;

    public NodeAdminStateUpdaterImpl(
            NodeRepository nodeRepository,
            Orchestrator orchestrator,
            NodeAdmin nodeAdmin,
            HostName hostHostname,
            Clock clock,
            Duration nodeAdminConvergeStateInterval) {
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.nodeAdmin = nodeAdmin;
        this.hostHostname = hostHostname.value();
        this.clock = clock;
        this.nodeAdminConvergeStateInterval = nodeAdminConvergeStateInterval;
        this.lastTick = clock.instant();

        this.loopThread = new Thread(() -> {
            nodeAdmin.start();

            while (! terminated.get()) {
                tick();
            }
        });
        this.loopThread.setName("tick-NodeAdminStateUpdater");
    }

    @Override
    public void setResumeStateAndCheckIfResumed(State wantedState) {
        synchronized (monitor) {
            if (this.wantedState != wantedState) {
                log.info("Wanted state change: " + this.wantedState + " -> " + wantedState);
                this.wantedState = wantedState;
                setLastConvergenceException(null);
                signalWorkToBeDone();
            }

            if (currentState != wantedState) {
                throw Optional.ofNullable(lastConvergenceException)
                        .orElseGet(() -> new RuntimeException(TRANSITION_EXCEPTION_MESSAGE));
            }
        }
    }

    void signalWorkToBeDone() {
        synchronized (monitor) {
            if (! workToDoNow) {
                workToDoNow = true;
                monitor.notifyAll();
            }
        }
    }

    void tick() {
        State wantedStateCopy;
        synchronized (monitor) {
            while (! workToDoNow) {
                Duration timeSinceLastConverge = Duration.between(lastTick, clock.instant());
                long remainder = nodeAdminConvergeStateInterval.minus(timeSinceLastConverge).toMillis();
                if (remainder > 0) {
                    try {
                        monitor.wait(remainder);
                    } catch (InterruptedException e) {
                        log.info("Interrupted, but ignoring this: NodeAdminStateUpdater");
                    }
                } else break;
            }
            lastTick = clock.instant();
            workToDoNow = false;

            // wantedState may change asynchronously, so we grab a copy of it here
            wantedStateCopy = this.wantedState;
        }

        try {
            convergeState(wantedStateCopy);
            setLastConvergenceException(null);
        } catch (OrchestratorException | ConvergenceException | HttpException e) {
            setLastConvergenceException(e);
            log.info("Unable to converge to " + wantedStateCopy + ": " + e.getMessage());
        } catch (RuntimeException e) {
            setLastConvergenceException(e);
            log.log(LogLevel.ERROR, "Error while trying to converge to " + wantedStateCopy, e);
        }

        if (wantedStateCopy != RESUMED && currentState == TRANSITIONING) {
            Duration subsystemFreezeDuration = nodeAdmin.subsystemFreezeDuration();
            if (subsystemFreezeDuration.compareTo(FREEZE_CONVERGENCE_TIMEOUT) > 0) {
                // We have spent too much time trying to freeze and node admin is still not frozen.
                // To avoid node agents stalling for too long, we'll force unfrozen ticks now.
                log.info("Timed out trying to freeze, will force unfreezed ticks");
                fetchContainersToRunFromNodeRepository();
                nodeAdmin.setFrozen(false);
            }
        } else if (currentState == RESUMED) {
            fetchContainersToRunFromNodeRepository();
        }
    }

    private void setLastConvergenceException(RuntimeException exception) {
        synchronized (monitor) {
            lastConvergenceException = exception;
        }
    }

    /**
     * This method attempts to converge node-admin w/agents to a {@link State}
     * with respect to: freeze, Orchestrator, and services running.
     */
    private void convergeState(State wantedState) {
        if (currentState == wantedState) return;
        synchronized (monitor) {
            currentState = TRANSITIONING;
        }

        boolean wantFrozen = wantedState != RESUMED;
        if (!nodeAdmin.setFrozen(wantFrozen)) {
            throw new ConvergenceException("NodeAdmin is not yet " + (wantFrozen ? "frozen" : "unfrozen"));
        }

        boolean hostIsActiveInNR = nodeRepository.getNode(hostHostname).getState() == Node.State.active;
        switch (wantedState) {
            case RESUMED:
                if (hostIsActiveInNR) orchestrator.resume(hostHostname);
                break;
            case SUSPENDED_NODE_ADMIN:
                if (hostIsActiveInNR) orchestrator.suspend(hostHostname);
                break;
            case SUSPENDED:
                // Fetch active nodes from node repo before suspending nodes.
                // It is only possible to suspend active nodes,
                // the orchestrator will fail if trying to suspend nodes in other states.
                // Even though state is frozen we need to interact with node repo, but
                // the data from node repo should not be used for anything else.
                // We should also suspend host's hostname to suspend node-admin
                List<String> nodesInActiveState = getNodesInActiveState();

                List<String> nodesToSuspend = new ArrayList<>(nodesInActiveState);
                if (hostIsActiveInNR) nodesToSuspend.add(hostHostname);
                if (!nodesToSuspend.isEmpty()) {
                    orchestrator.suspend(hostHostname, nodesToSuspend);
                    log.info("Orchestrator allows suspension of " + nodesToSuspend);
                }

                // The node agent services are stopped by this thread, which is OK only
                // because the node agents are frozen (see above).
                nodeAdmin.stopNodeAgentServices(nodesInActiveState);
                break;
            default:
                throw new IllegalStateException("Unknown wanted state " + wantedState);
        }

        log.info("State changed from " + currentState + " to " + wantedState);
        synchronized (monitor) {
            // Writes to currentState must be synchronized. Reads doesn't have to since this thread
            // is the only one modifying it.
            currentState = wantedState;
        }
    }

    private void fetchContainersToRunFromNodeRepository() {
        try {
            final List<NodeSpec> containersToRun = nodeRepository.getNodes(hostHostname);
            nodeAdmin.refreshContainersToRun(containersToRun);
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Failed to update which containers should be running", e);
        }
    }

    private List<String> getNodesInActiveState() {
        return nodeRepository.getNodes(hostHostname)
                             .stream()
                             .filter(node -> node.getState() == Node.State.active)
                             .map(NodeSpec::getHostname)
                             .collect(Collectors.toList());
    }

    public void start() {
        loopThread.start();
    }

    public void stop() {
        if (!terminated.compareAndSet(false, true)) {
            throw new RuntimeException("Can not re-stop a node agent.");
        }

        // First we need to stop NodeAdminStateUpdaterImpl thread to make sure no new NodeAgents are spawned
        signalWorkToBeDone();

        do {
            try {
                loopThread.join();
            } catch (InterruptedException e1) {
                log.info("Interrupted while waiting for NodeAdminStateUpdater thread and specVerfierScheduler to shutdown");
            }
        } while (loopThread.isAlive());

        // Finally, stop NodeAdmin and all the NodeAgents
        nodeAdmin.stop();
    }
}
