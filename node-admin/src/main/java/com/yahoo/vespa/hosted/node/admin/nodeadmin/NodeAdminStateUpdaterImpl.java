// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.concurrent.classlock.ClassLock;
import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.concurrent.classlock.LockInterruptException;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.hosted.provision.Node;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
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

    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private State currentState = SUSPENDED_NODE_ADMIN;
    private State wantedState = RESUMED;
    private boolean workToDoNow = true;

    private final Object monitor = new Object();

    private final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());
    private final ScheduledExecutorService specVerifierScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("specverifier"));
    private final Thread loopThread;

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final NodeAdmin nodeAdmin;
    private final Clock clock;
    private final String dockerHostHostName;
    private final Duration nodeAdminConvergeStateInterval;

    private final Optional<ClassLocking> classLocking;
    private Optional<ClassLock> classLock = Optional.empty();
    private Instant lastTick;

    public NodeAdminStateUpdaterImpl(
            NodeRepository nodeRepository,
            Orchestrator orchestrator,
            StorageMaintainer storageMaintainer,
            NodeAdmin nodeAdmin,
            String dockerHostHostName,
            Clock clock,
            Duration nodeAdminConvergeStateInterval,
            Optional<ClassLocking> classLocking) {
        log.info(objectToString() + ": Creating object");
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.nodeAdmin = nodeAdmin;
        this.dockerHostHostName = dockerHostHostName;
        this.clock = clock;
        this.nodeAdminConvergeStateInterval = nodeAdminConvergeStateInterval;
        this.classLocking = classLocking;
        this.lastTick = clock.instant();

        this.loopThread = new Thread(() -> {
            if (classLocking.isPresent()) {
                log.info(objectToString() + ": Acquiring lock");
                try {
                    classLock = Optional.of(classLocking.get().lockWhile(NodeAdminStateUpdater.class, () -> !terminated.get()));
                } catch (LockInterruptException e) {
                    classLock = Optional.empty();
                    return;
                }
            }

            log.info(objectToString() + ": Starting threads and schedulers");
            nodeAdmin.start();
            specVerifierScheduler.scheduleWithFixedDelay(() ->
                    updateHardwareDivergence(storageMaintainer), 5, 60, TimeUnit.MINUTES);

            while (! terminated.get()) {
                tick();
            }
        });
        this.loopThread.setName("tick-NodeAdminStateUpdater");
    }

    private String objectToString() {
        return this.getClass().getSimpleName() + "@" + Integer.toString(System.identityHashCode(this));
    }

    @Override
    public Map<String, Object> getDebugPage() {
        Map<String, Object> debug = new LinkedHashMap<>();
        synchronized (monitor) {
            debug.put("dockerHostHostName", dockerHostHostName);
            debug.put("wantedState", wantedState);
            debug.put("currentState", currentState);
            debug.put("NodeAdmin", nodeAdmin.debugInfo());
        }
        return debug;
    }

    private void updateHardwareDivergence(StorageMaintainer maintainer) {
        if (currentState != RESUMED) return;

        try {
            NodeSpec node = nodeRepository.getNode(dockerHostHostName)
                    .orElseThrow(() -> new RuntimeException("Failed to get host's node spec from node-repo"));
            String hardwareDivergence = maintainer.getHardwareDivergence(node);

            // Only update hardware divergence if there is a change.
            if (!node.hardwareDivergence.orElse("null").equals(hardwareDivergence)) {
                NodeAttributes nodeAttributes = new NodeAttributes().withHardwareDivergence(hardwareDivergence);
                nodeRepository.updateNodeAttributes(dockerHostHostName, nodeAttributes);
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to report hardware divergence", e);
        }
    }

    @Override
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
        } catch (OrchestratorException | ConvergenceException | HttpException e) {
            log.info("Unable to converge to " + wantedStateCopy + ": " + e.getMessage());
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Error while trying to converge to " + wantedStateCopy, e);
        }

        if (wantedStateCopy != RESUMED && currentState == TRANSITIONING) {
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
        if (currentState == wantedState) return;
        synchronized (monitor) {
            currentState = TRANSITIONING;
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
                log.info("Orchestrator allows suspension of " + nodesToSuspend);

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

            try {
                final List<NodeSpec> containersToRun = nodeRepository.getNodes(dockerHostHostName);
                nodeAdmin.refreshContainersToRun(containersToRun);
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to update which containers should be running", e);
            }
        }
    }

    private List<String> getNodesInActiveState() {
        return nodeRepository.getNodes(dockerHostHostName)
                             .stream()
                             .filter(node -> node.nodeState == Node.State.active)
                             .map(node -> node.hostname)
                             .collect(Collectors.toList());
    }

    public void start() {
        loopThread.start();
    }

    public void stop() {
        log.info(objectToString() + ": Stop called");
        if (!terminated.compareAndSet(false, true)) {
            throw new RuntimeException("Can not re-stop a node agent.");
        }

        classLocking.ifPresent(ClassLocking::interrupt);

        // First we need to stop NodeAdminStateUpdaterImpl thread to make sure no new NodeAgents are spawned
        signalWorkToBeDone();
        specVerifierScheduler.shutdown();

        do {
            try {
                loopThread.join();
                specVerifierScheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e1) {
                log.info("Interrupted while waiting for NodeAdminStateUpdater thread and specVerfierScheduler to shutdown");
            }
        } while (loopThread.isAlive() || !specVerifierScheduler.isTerminated());

        // Finally, stop NodeAdmin and all the NodeAgents
        nodeAdmin.stop();

        classLock.ifPresent(lock -> {
            log.info(objectToString() + ": Releasing lock");
            lock.close();
        });
        log.info(objectToString() + ": Stop complete");
    }
}
