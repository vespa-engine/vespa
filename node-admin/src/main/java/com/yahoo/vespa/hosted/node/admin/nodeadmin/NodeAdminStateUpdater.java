// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdater extends AbstractComponent {
    public static final Duration FREEZE_CONVERGENCE_TIMEOUT = Duration.ofMinutes(5);

    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private State currentState = SUSPENDED_NODE_ADMIN;
    private State wantedState = RESUMED;
    private boolean workToDoNow = true;

    private final Object monitor = new Object();

    private final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());
    private Thread loopThread;

    private final NodeRepository nodeRepository;
    private final NodeAdmin nodeAdmin;
    private final Clock clock;
    private final Orchestrator orchestrator;
    private final String dockerHostHostName;

    private long delaysBetweenEachTickMillis = 30_000;
    private Instant lastTick;

    public NodeAdminStateUpdater(
            final NodeRepository nodeRepository,
            final NodeAdmin nodeAdmin,
            Clock clock,
            Orchestrator orchestrator,
            String dockerHostHostName) {
        this.nodeRepository = nodeRepository;
        this.nodeAdmin = nodeAdmin;
        this.clock = clock;
        this.orchestrator = orchestrator;
        this.dockerHostHostName = dockerHostHostName;
        this.lastTick = clock.instant();
    }

    public enum State { RESUMED, SUSPENDED_NODE_ADMIN, SUSPENDED}

    public Map<String, Object> getDebugPage() {
        Map<String, Object> debug = new LinkedHashMap<>();
        synchronized (monitor) {
            debug.put("dockerHostHostName", dockerHostHostName);
            debug.put("NodeAdmin", nodeAdmin.debugInfo());
            debug.put("Wanted State: ", wantedState);
            debug.put("Current State: ", currentState);
        }
        return debug;
    }

    public boolean setResumeStateAndCheckIfResumed(State wantedState) {
        synchronized (monitor) {
            if (this.wantedState != wantedState) {
                log.info("Wanted state change: " + this.wantedState + " -> " + wantedState);
                this.wantedState = wantedState;
                signalWorkToBeDone();
            }

            return currentState == wantedState;
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
        State wantedState = null;
        synchronized (monitor) {
            while (! workToDoNow) {
                long remainder = delaysBetweenEachTickMillis - Duration.between(lastTick, clock.instant()).toMillis();
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

            if (currentState != this.wantedState) {
                wantedState = this.wantedState;
            }
        }

        if (wantedState != null) { // There is a state we want to be in, but aren't right now
            boolean converged = false;
            try {
                convergeState(wantedState);
                converged = true;
            } catch (OrchestratorException | ConvergenceException e) {
                log.info("Unable to converge to " + wantedState + ": " + e.getMessage());
            } catch (Exception e) {
                log.log(LogLevel.ERROR, "Error while trying to converge to " + wantedState, e);
            }

            if (wantedState != RESUMED && !converged) {
                Duration subsystemFreezeDuration = nodeAdmin.subsystemFreezeDuration();
                if (subsystemFreezeDuration.compareTo(FREEZE_CONVERGENCE_TIMEOUT) > 0) {
                    // We have spent too long time trying to freeze and node admin is still not frozen.
                    // To avoid node agents stalling for too long, we'll force unfrozen ticks now.
                    log.info("Timed out trying to freeze, will force unfreezed ticks");
                    nodeAdmin.setFrozen(false);
                }
            }
        }

        fetchContainersToRunFromNodeRepository();
    }

    /**
     * This method attempts to converge node-admin towards one of the {@link State}
     */
    private void convergeState(State wantedState) {
        boolean wantFrozen = wantedState != RESUMED;
        if (!nodeAdmin.setFrozen(wantFrozen)) {
            throw new ConvergenceException("NodeAdmin is not yet " + (wantFrozen ? "frozen" : "unfrozen"));
        }

        if (wantedState == RESUMED) {
            orchestrator.resume(dockerHostHostName);
            if (wantedState == updateAndGetCurrentState(RESUMED)) return;
        }

        // Fetch active nodes from node repo before suspending nodes.
        // It is only possible to suspend active nodes,
        // the orchestrator will fail if trying to suspend nodes in other states.
        // Even though state is frozen we need to interact with node repo, but
        // the data from node repo should not be used for anything else.
        // We should also suspend host's hostname to suspend node-admin
        List<String> nodesInActiveState;
        try {
            nodesInActiveState = getNodesInActiveState();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get nodes from node repo", e);
        }

        if (currentState == RESUMED) {
            List<String> nodesToSuspend = new ArrayList<>(nodesInActiveState);
            nodesToSuspend.add(dockerHostHostName);
            orchestrator.suspend(dockerHostHostName, nodesToSuspend);

            if (wantedState == updateAndGetCurrentState(SUSPENDED_NODE_ADMIN)) return;
        }

        nodeAdmin.stopNodeAgentServices(nodesInActiveState);
        updateAndGetCurrentState(SUSPENDED);
    }

    private State updateAndGetCurrentState(State currentState) {
        synchronized (monitor) {
            log.info("State change: " + this.currentState + " -> " + currentState
                    + (currentState == wantedState ? " [converged]" : ""));
            this.currentState = currentState;
            return currentState;
        }
    }

    private void fetchContainersToRunFromNodeRepository() {
        synchronized (monitor) {
            // Refresh containers to run even if we would like to suspend but have failed to do so yet,
            // because it may take a long time to get permission to suspend.
            if (currentState != RESUMED) {
                log.info("Frozen, skipping fetching info from node repository");
                return;
            }
            final List<ContainerNodeSpec> containersToRun;
            try {
                containersToRun = nodeRepository.getContainersToRun();
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed fetching container info from node repository", e);
                return;
            }
            if (containersToRun == null) {
                log.warning("Got null from node repository");
                return;
            }
            try {
                nodeAdmin.refreshContainersToRun(containersToRun);
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed updating node admin: ", e);
            }
        }
    }

    private List<String> getNodesInActiveState() throws IOException {
        return nodeRepository.getContainersToRun()
                             .stream()
                             .filter(nodespec -> nodespec.nodeState == Node.State.active)
                             .map(nodespec -> nodespec.hostname)
                             .collect(Collectors.toList());
    }

    public void start(long stateConvergeInterval) {
        delaysBetweenEachTickMillis = stateConvergeInterval;
        if (loopThread != null) {
            throw new RuntimeException("Can not restart NodeAdminStateUpdater");
        }

        loopThread = new Thread(() -> {
            while (! terminated.get()) tick();
        });
        loopThread.setName("tick-NodeAdminStateUpdater");
        loopThread.start();
    }

    @Override
    public void deconstruct() {
        if (!terminated.compareAndSet(false, true)) {
            throw new RuntimeException("Can not re-stop a node agent.");
        }
        signalWorkToBeDone();
        try {
            loopThread.join(10000);
            if (loopThread.isAlive()) {
                log.log(LogLevel.ERROR, "Could not stop tick thread");
            }
        } catch (InterruptedException e1) {
            log.log(LogLevel.ERROR, "Interrupted; Could not stop thread");
        }
        nodeAdmin.shutdown();
    }
}
