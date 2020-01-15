// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.provision.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextFactory;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.TRANSITIONING;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdater {
    private static final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());
    private static final Duration FREEZE_CONVERGENCE_TIMEOUT = Duration.ofMinutes(5);

    private final ScheduledExecutorService metricsScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("metricsscheduler"));

    private final CachedSupplier<Map<String, Acl>> cachedAclSupplier;
    private final NodeAgentContextFactory nodeAgentContextFactory;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final NodeAdmin nodeAdmin;
    private final String hostHostname;

    public enum State { TRANSITIONING, RESUMED, SUSPENDED_NODE_ADMIN, SUSPENDED }

    private volatile State currentState = SUSPENDED_NODE_ADMIN;

    public NodeAdminStateUpdater(
            NodeAgentContextFactory nodeAgentContextFactory,
            NodeRepository nodeRepository,
            Orchestrator orchestrator,
            NodeAdmin nodeAdmin,
            HostName hostHostname,
            Clock clock) {
        this.nodeAgentContextFactory = nodeAgentContextFactory;
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.nodeAdmin = nodeAdmin;
        this.hostHostname = hostHostname.value();
        this.cachedAclSupplier = new CachedSupplier<>(clock, Duration.ofSeconds(115), () -> nodeRepository.getAcls(this.hostHostname));
    }

    public void start() {
        nodeAdmin.start();

        EnumSet<State> suspendedStates = EnumSet.of(SUSPENDED_NODE_ADMIN, SUSPENDED);
        metricsScheduler.scheduleAtFixedRate(() -> {
            try {
                nodeAdmin.updateMetrics(suspendedStates.contains(currentState));
            } catch (Throwable e) {
                log.log(Level.WARNING, "Metric fetcher scheduler failed", e);
            }
        }, 10, 55, TimeUnit.SECONDS);
    }

    public void stop() {
        metricsScheduler.shutdown();

        // Stop all node-agents in parallel, will block until the last NodeAgent is stopped
        nodeAdmin.stop();

        do {
            try {
                metricsScheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                log.info("Was interrupted while waiting for metricsScheduler and shutdown");
            }
        } while (!metricsScheduler.isTerminated());
    }

    /**
     * This method attempts to converge node-admin w/agents to a {@link State}
     * with respect to: freeze, Orchestrator, and services running.
     */
    public void converge(State wantedState) {
        if (wantedState == RESUMED) {
            adjustNodeAgentsToRunFromNodeRepository();
        } else if (currentState == TRANSITIONING && nodeAdmin.subsystemFreezeDuration().compareTo(FREEZE_CONVERGENCE_TIMEOUT) > 0) {
            // We have spent too much time trying to freeze and node admin is still not frozen.
            // To avoid node agents stalling for too long, we'll force unfrozen ticks now.
            adjustNodeAgentsToRunFromNodeRepository();
            nodeAdmin.setFrozen(false);
            throw new ConvergenceException("Timed out trying to freeze all nodes: will force an unfrozen tick");
        }

        if (currentState == wantedState) return;
        currentState = TRANSITIONING;

        boolean wantFrozen = wantedState != RESUMED;
        if (!nodeAdmin.setFrozen(wantFrozen)) {
            throw new ConvergenceException("NodeAdmin is not yet " + (wantFrozen ? "frozen" : "unfrozen"));
        }

        boolean hostIsActiveInNR = nodeRepository.getNode(hostHostname).state() == NodeState.active;
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
        currentState = wantedState;
    }

    void adjustNodeAgentsToRunFromNodeRepository() {
        try {
            Map<String, NodeSpec> nodeSpecByHostname = nodeRepository.getNodes(hostHostname).stream()
                    .collect(Collectors.toMap(NodeSpec::hostname, Function.identity()));
            Map<String, Acl> aclByHostname = Optional.of(cachedAclSupplier.get())
                    .filter(acls -> acls.keySet().containsAll(nodeSpecByHostname.keySet()))
                    .orElseGet(cachedAclSupplier::invalidateAndGet);

            Set<NodeAgentContext> nodeAgentContexts = nodeSpecByHostname.keySet().stream()
                    .map(hostname -> nodeAgentContextFactory.create(
                            nodeSpecByHostname.get(hostname),
                            aclByHostname.getOrDefault(hostname, Acl.EMPTY)))
                    .collect(Collectors.toSet());
            nodeAdmin.refreshContainersToRun(nodeAgentContexts);
        } catch (ConvergenceException e) {
            log.log(LogLevel.WARNING, "Failed to update which containers should be running: " + Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(LogLevel.WARNING, "Failed to update which containers should be running", e);
        }
    }

    private List<String> getNodesInActiveState() {
        return nodeRepository.getNodes(hostHostname)
                             .stream()
                             .filter(node -> node.state() == NodeState.active)
                             .map(NodeSpec::hostname)
                             .collect(Collectors.toList());
    }

    private static class CachedSupplier<T> implements Supplier<T> {
        private final Clock clock;
        private final Duration expiration;
        private final Supplier<T> supplier;
        private Instant refreshAt;
        private T cachedValue;

        private CachedSupplier(Clock clock, Duration expiration, Supplier<T> supplier) {
            this.clock = clock;
            this.expiration = expiration;
            this.supplier = supplier;
            this.refreshAt = Instant.MIN;
        }

        @Override
        public T get() {
            if (! clock.instant().isBefore(refreshAt)) {
                cachedValue = supplier.get();
                refreshAt = clock.instant().plus(expiration);
            }
            return cachedValue;
        }

        private T invalidateAndGet() {
            refreshAt = Instant.MIN;
            return get();
        }
    }
}
