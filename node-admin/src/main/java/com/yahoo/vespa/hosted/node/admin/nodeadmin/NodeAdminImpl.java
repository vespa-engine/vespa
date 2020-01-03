// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.vespa.hosted.dockerapi.metrics.Counter;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.Gauge;
import com.yahoo.vespa.hosted.dockerapi.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextManager;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentFactory;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administers a host (for now only docker hosts) and its nodes (docker containers nodes).
 *
 * @author stiankri
 */
public class NodeAdminImpl implements NodeAdmin {
    private static final Duration NODE_AGENT_FREEZE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration NODE_AGENT_SPREAD = Duration.ofSeconds(3);

    private final NodeAgentWithSchedulerFactory nodeAgentWithSchedulerFactory;

    private final Clock clock;
    private final Duration freezeTimeout;
    private final Duration spread;
    private boolean previousWantFrozen;
    private boolean isFrozen;
    private Instant startOfFreezeConvergence;
    private final Map<String, NodeAgentWithScheduler> nodeAgentWithSchedulerByHostname = new ConcurrentHashMap<>();

    private final Gauge jvmHeapUsed;
    private final Gauge jvmHeapFree;
    private final Gauge jvmHeapTotal;
    private final Counter numberOfUnhandledExceptions;

    public NodeAdminImpl(NodeAgentFactory nodeAgentFactory, Metrics metrics, Clock clock) {
        this((NodeAgentWithSchedulerFactory) nodeAgentContext -> create(clock, nodeAgentFactory, nodeAgentContext),
                metrics, clock, NODE_AGENT_FREEZE_TIMEOUT, NODE_AGENT_SPREAD);
    }

    public NodeAdminImpl(NodeAgentFactory nodeAgentFactory, Metrics metrics,
                         Clock clock, Duration freezeTimeout, Duration spread) {
        this((NodeAgentWithSchedulerFactory) nodeAgentContext -> create(clock, nodeAgentFactory, nodeAgentContext),
                metrics, clock, freezeTimeout, spread);
    }

    NodeAdminImpl(NodeAgentWithSchedulerFactory nodeAgentWithSchedulerFactory,
                  Metrics metrics, Clock clock, Duration freezeTimeout, Duration spread) {
        this.nodeAgentWithSchedulerFactory = nodeAgentWithSchedulerFactory;

        this.clock = clock;
        this.freezeTimeout = freezeTimeout;
        this.spread = spread;
        this.previousWantFrozen = true;
        this.isFrozen = true;
        this.startOfFreezeConvergence = clock.instant();

        this.numberOfUnhandledExceptions = metrics.declareCounter("unhandled_exceptions",
                new Dimensions(Map.of("src", "node-agents")));

        this.jvmHeapUsed = metrics.declareGauge("mem.heap.used");
        this.jvmHeapFree = metrics.declareGauge("mem.heap.free");
        this.jvmHeapTotal = metrics.declareGauge("mem.heap.total");
    }

    @Override
    public void refreshContainersToRun(Set<NodeAgentContext> nodeAgentContexts) {
        Map<String, NodeAgentContext> nodeAgentContextsByHostname = nodeAgentContexts.stream()
                .collect(Collectors.toMap(nac -> nac.hostname().value(), Function.identity()));

        // Stop and remove NodeAgents that should no longer be running
        diff(nodeAgentWithSchedulerByHostname.keySet(), nodeAgentContextsByHostname.keySet())
                .forEach(hostname -> nodeAgentWithSchedulerByHostname.remove(hostname).stopForRemoval());

        // Start NodeAgent for hostnames that should be running, but aren't yet
        diff(nodeAgentContextsByHostname.keySet(), nodeAgentWithSchedulerByHostname.keySet()).forEach(hostname ->  {
            NodeAgentWithScheduler naws = nodeAgentWithSchedulerFactory.create(nodeAgentContextsByHostname.get(hostname));
            naws.start();
            nodeAgentWithSchedulerByHostname.put(hostname, naws);
        });

        Duration timeBetweenNodeAgents = spread.dividedBy(Math.max(nodeAgentContextsByHostname.size() - 1, 1));
        Instant nextAgentStart = clock.instant();
        // At this point, nodeAgentContextsByHostname and nodeAgentWithSchedulerByHostname should have the same keys
        for (Map.Entry<String, NodeAgentContext> entry : nodeAgentContextsByHostname.entrySet()) {
            nodeAgentWithSchedulerByHostname.get(entry.getKey()).scheduleTickWith(entry.getValue(), nextAgentStart);
            nextAgentStart = nextAgentStart.plus(timeBetweenNodeAgents);
        }
    }

    @Override
    public void updateMetrics(boolean isSuspended) {
        for (NodeAgentWithScheduler nodeAgentWithScheduler : nodeAgentWithSchedulerByHostname.values()) {
            if (!isSuspended) numberOfUnhandledExceptions.add(nodeAgentWithScheduler.getAndResetNumberOfUnhandledExceptions());
            nodeAgentWithScheduler.updateContainerNodeMetrics(isSuspended);
        }

        if (!isSuspended) {
            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long usedMemory = totalMemory - freeMemory;
            jvmHeapFree.sample(freeMemory);
            jvmHeapUsed.sample(usedMemory);
            jvmHeapTotal.sample(totalMemory);
        }
    }

    @Override
    public boolean setFrozen(boolean wantFrozen) {
        if (wantFrozen != previousWantFrozen) {
            if (wantFrozen) {
                this.startOfFreezeConvergence = clock.instant();
            } else {
                this.startOfFreezeConvergence = null;
            }

            previousWantFrozen = wantFrozen;
        }

        // Use filter with count instead of allMatch() because allMatch() will short circuit on first non-match
        boolean allNodeAgentsConverged = nodeAgentWithSchedulerByHostname.values().parallelStream()
                .filter(nodeAgentScheduler -> !nodeAgentScheduler.setFrozen(wantFrozen, freezeTimeout))
                .count() == 0;

        if (wantFrozen) {
            if (allNodeAgentsConverged) isFrozen = true;
        } else isFrozen = false;

        return allNodeAgentsConverged;
    }

    @Override
    public boolean isFrozen() {
        return isFrozen;
    }

    @Override
    public Duration subsystemFreezeDuration() {
        if (startOfFreezeConvergence == null) {
            return Duration.ZERO;
        } else {
            return Duration.between(startOfFreezeConvergence, clock.instant());
        }
    }

    @Override
    public void stopNodeAgentServices(List<String> hostnames) {
        // Each container may spend 1-1:30 minutes stopping
        hostnames.parallelStream()
                 .filter(nodeAgentWithSchedulerByHostname::containsKey)
                 .map(nodeAgentWithSchedulerByHostname::get)
                 .forEach(NodeAgentWithScheduler::stopForHostSuspension);
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        // Stop all node-agents in parallel, will block until the last NodeAgent is stopped
        nodeAgentWithSchedulerByHostname.values().parallelStream().forEach(NodeAgentWithScheduler::stopForRemoval);
    }

    // Set-difference. Returns minuend minus subtrahend.
    private static <T> Set<T> diff(Set<T> minuend, Set<T> subtrahend) {
        var result = new HashSet<>(minuend);
        result.removeAll(subtrahend);
        return result;
    }

    static class NodeAgentWithScheduler implements NodeAgentScheduler {
        private final NodeAgent nodeAgent;
        private final NodeAgentScheduler nodeAgentScheduler;

        private NodeAgentWithScheduler(NodeAgent nodeAgent, NodeAgentScheduler nodeAgentScheduler) {
            this.nodeAgent = nodeAgent;
            this.nodeAgentScheduler = nodeAgentScheduler;
        }

        void start() { nodeAgent.start(currentContext()); }
        void stopForHostSuspension() { nodeAgent.stopForHostSuspension(currentContext()); }
        void stopForRemoval() { nodeAgent.stopForRemoval(currentContext()); }
        void updateContainerNodeMetrics(boolean isSuspended) { nodeAgent.updateContainerNodeMetrics(currentContext(), isSuspended); }
        int getAndResetNumberOfUnhandledExceptions() { return nodeAgent.getAndResetNumberOfUnhandledExceptions(); }

        @Override public void scheduleTickWith(NodeAgentContext context, Instant at) { nodeAgentScheduler.scheduleTickWith(context, at); }
        @Override public boolean setFrozen(boolean frozen, Duration timeout) { return nodeAgentScheduler.setFrozen(frozen, timeout); }
        @Override public NodeAgentContext currentContext() { return nodeAgentScheduler.currentContext(); }
    }

    @FunctionalInterface
    interface NodeAgentWithSchedulerFactory {
        NodeAgentWithScheduler create(NodeAgentContext context);
    }

    private static NodeAgentWithScheduler create(Clock clock, NodeAgentFactory nodeAgentFactory, NodeAgentContext context) {
        NodeAgentContextManager contextManager = new NodeAgentContextManager(clock, context);
        NodeAgent nodeAgent = nodeAgentFactory.create(contextManager, context);
        return new NodeAgentWithScheduler(nodeAgent, contextManager);
    }
}
