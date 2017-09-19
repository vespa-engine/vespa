// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdater extends AbstractComponent {
    static final Duration FREEZE_CONVERGENCE_TIMEOUT = Duration.ofMinutes(5);

    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private State currentState = SUSPENDED_NODE_ADMIN;
    private State wantedState = RESUMED;
    private boolean workToDoNow = true;

    private final Object monitor = new Object();

    private final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());
    private final ScheduledExecutorService specVerifierScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("specverifier"));
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
            StorageMaintainer storageMaintainer,
            Clock clock,
            Orchestrator orchestrator,
            String dockerHostHostName) {
        log.log(LogLevel.INFO, objectToString() + ": Creating object");
        this.nodeRepository = nodeRepository;
        this.nodeAdmin = nodeAdmin;
        this.clock = clock;
        this.orchestrator = orchestrator;
        this.dockerHostHostName = dockerHostHostName;
        this.lastTick = clock.instant();

        specVerifierScheduler.scheduleWithFixedDelay(() ->
                updateHardwareDivergence(storageMaintainer), 5, 60, TimeUnit.MINUTES);
    }

    private String objectToString() {
        return this.getClass().getSimpleName() + "@" + Integer.toString(System.identityHashCode(this));
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

    private void updateHardwareDivergence(StorageMaintainer maintainer) {
        if (currentState != RESUMED) return;

        try {
            String hardwareDivergence = maintainer.getHardwareDivergence();
            NodeAttributes nodeAttributes = new NodeAttributes().withHardwareDivergence(hardwareDivergence);
            nodeRepository.updateNodeAttributes(dockerHostHostName, nodeAttributes);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to report hardware divergence", e);
        }
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
        State wantedStateCopy;
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

            // wantedState may change asynchronously, so we grab a copy of it here
            wantedStateCopy = this.wantedState;
        }

        try {
            convergeState(wantedStateCopy);
        } catch (OrchestratorException | ConvergenceException e) {
            log.info("Unable to converge to " + wantedStateCopy + ": " + e.getMessage());
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Error while trying to converge to " + wantedStateCopy, e);
        }

        if (wantedStateCopy != RESUMED && currentState == RESUMED) {
            Duration subsystemFreezeDuration = nodeAdmin.subsystemFreezeDuration();
            if (subsystemFreezeDuration.compareTo(FREEZE_CONVERGENCE_TIMEOUT) > 0) {
                // We have spent too much time trying to freeze and node admin is still not frozen.
                // To avoid node agents stalling for too long, we'll force unfrozen ticks now.
                log.info("Timed out trying to freeze, will force unfreezed ticks");
                nodeAdmin.setFrozen(false);
            }
        }

        fetchContainersToRunFromNodeRepository();
    }

    /**
     * This method attempts to converge node-admin w/agents to a {@link State}
     * with respect to: freeze, Orchestrator, and services running.
     */
    private void convergeState(State wantedState) {
        if (currentState == wantedState) {
            return;
        }

        boolean wantFrozen = wantedState != RESUMED;
        if (!nodeAdmin.setFrozen(wantFrozen)) {
            throw new ConvergenceException("NodeAdmin is not yet " + (wantFrozen ? "frozen" : "unfrozen"));
        }

        switch (wantedState) {
            case RESUMED:
                orchestrator.resume(dockerHostHostName);
                break;
            case SUSPENDED_NODE_ADMIN:
                orchestrator.suspend(dockerHostHostName);
                break;
            case SUSPENDED:
                // Fetch active nodes from node repo before suspending nodes.
                // It is only possible to suspend active nodes,
                // the orchestrator will fail if trying to suspend nodes in other states.
                // Even though state is frozen we need to interact with node repo, but
                // the data from node repo should not be used for anything else.
                // We should also suspend host's hostname to suspend node-admin
                List<String> nodesInActiveState = getNodesInActiveState();

                List<String> nodesToSuspend = new ArrayList<>();
                nodesToSuspend.addAll(nodesInActiveState);
                nodesToSuspend.add(dockerHostHostName);
                orchestrator.suspend(dockerHostHostName, nodesToSuspend);

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

    private List<String> getNodesInActiveState() {
        try {
            return nodeRepository.getContainersToRun()
                                 .stream()
                                 .filter(nodespec -> nodespec.nodeState == Node.State.active)
                                 .map(nodespec -> nodespec.hostname)
                                 .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to get nodes from node repo", e);
        }
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
        log.log(LogLevel.INFO, objectToString() + ": Deconstruct called");
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
        log.log(LogLevel.INFO, objectToString() + ": Deconstruct complete");
    }
}
