// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdater extends AbstractComponent {
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private State currentState = RESUMED;
    private State wantedState = RESUMED;
    private boolean workToDoNow = true;

    private final Object monitor = new Object();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final PrefixLogger logger = PrefixLogger.getNodeAdminLogger(NodeAdminStateUpdater.class);
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
                        logger.error("Interrupted, but ignoring this: NodeAdminStateUpdater");
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
            try {
                convergeState(wantedState);
            } catch (Exception e) {
                logger.error("Failed to converge NodeAdminStateUpdater", e);
            }
        }
    }

    /**
     * This method attempts to converge NodeAgent's and NodeAdmin's frozen state with their orchestrator
     * state. When trying to suspend node-admin, this method will first attempt to freeze all NodeAgents and
     * NodeAdmin, then asking orchestrator for permission to suspend node-admin app, and finally asking orchestrator
     * for permission to suspend all active nodes on this host, if either of the request is denied,
     * this method will unfreeze NodeAgents and NodeAdmin.
     */
    private void convergeState(State wantedState) {
        boolean wantFrozen = wantedState != RESUMED;
        if (!nodeAdmin.setFrozen(wantFrozen)) {
            throw new RuntimeException("NodeAdmin has not yet converged to " + (wantFrozen ? "frozen" : "unfrozen"));
        }

        // To get to resumed state, we only need to converge NodeAdmins frozen state
        if (wantedState == RESUMED) {
            synchronized (monitor) {
                currentState = RESUMED;
            }
            return;
        }

        if (currentState == RESUMED) {
            if (! orchestrator.suspend(dockerHostHostName)) {
                nodeAdmin.setFrozen(false);
                throw new RuntimeException("Failed to get permission to suspend node-admin, resuming.");
            }

            synchronized (monitor) {
                currentState = SUSPENDED_NODE_ADMIN;
            }
            if (wantedState == currentState) return;
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
            throw new RuntimeException("Failed to get nodes from node repo:" + e.getMessage());
        }

        if (nodesInActiveState.size() > 0) {
            orchestrator.suspend(dockerHostHostName, nodesInActiveState).ifPresent(orchestratorResponse -> {
                nodeAdmin.setFrozen(false);
                throw new RuntimeException("Failed to get permission to suspend, resuming. Reason: " + orchestratorResponse);
            });
            nodeAdmin.stopNodeAgentServices(nodesInActiveState);
        }

        synchronized (monitor) {
            currentState = SUSPENDED;
        }
    }

    private void fetchContainersToRunFromNodeRepository() {
        synchronized (monitor) {
            if (currentState != RESUMED) {
                logger.info("Frozen, skipping fetching info from node repository");
                return;
            }
            final List<ContainerNodeSpec> containersToRun;
            try {
                containersToRun = nodeRepository.getContainersToRun();
            } catch (Throwable t) {
                logger.warning("Failed fetching container info from node repository", t);
                return;
            }
            if (containersToRun == null) {
                logger.warning("Got null from node repository");
                return;
            }
            try {
                nodeAdmin.refreshContainersToRun(containersToRun);
            } catch (Throwable t) {
                logger.warning("Failed updating node admin: ", t);
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

    public void start(long stateConvergeInterval, long fetchContainersInterval) {
        delaysBetweenEachTickMillis = stateConvergeInterval;
        if (loopThread != null) {
            throw new RuntimeException("Can not restart NodeAdminStateUpdater");
        }

        loopThread = new Thread(() -> {
            while (! terminated.get()) tick();
        });
        loopThread.setName("tick-NodeAdminStateUpdater");
        loopThread.start();

        scheduler.scheduleWithFixedDelay(
                this::fetchContainersToRunFromNodeRepository,
                0,
                fetchContainersInterval,
                MILLISECONDS);
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
                logger.error("Could not stop NodeAdminStateUpdater tick thread");
            }
            scheduler.shutdown();
            if (! scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Could not stop NodeAdminStateUpdater fetch containers scheduler.");
            }
        } catch (InterruptedException e1) {
            logger.error("Interrupted; Could not stop NodeAdminStateUpdater thread");
        }
        nodeAdmin.shutdown();
    }
}
