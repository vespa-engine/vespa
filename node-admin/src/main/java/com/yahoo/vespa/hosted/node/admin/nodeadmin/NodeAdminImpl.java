// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.hosted.node.admin.container.ContainerStats;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Counter;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Dimensions;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Gauge;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextManager;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentFactory;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentScheduler;

import java.nio.file.FileSystem;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Administers a host (for now only docker hosts) and its nodes (docker containers nodes).
 *
 * @author stiankri
 */
public class NodeAdminImpl implements NodeAdmin {
    private static final Duration NODE_AGENT_FREEZE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration NODE_AGENT_SPREAD = Duration.ofSeconds(3);

    private final NodeAgentWithSchedulerFactory nodeAgentWithSchedulerFactory;

    private final Timer timer;
    private final Duration freezeTimeout;
    private final Duration spread;
    private boolean previousWantFrozen;
    private boolean isFrozen;
    private Instant startOfFreezeConvergence;
    private final Map<String, NodeAgentWithScheduler> nodeAgentWithSchedulerByHostname = new ConcurrentHashMap<>();

    private final ProcMeminfoReader procMeminfoReader;
    private final Gauge jvmHeapUsed;
    private final Gauge jvmHeapFree;
    private final Gauge jvmHeapTotal;
    private final Gauge containerCount;
    private final Counter numberOfUnhandledExceptions;
    private final Metrics metrics;
    private Dimensions previousMemoryOverheadDimensions = null;

    public NodeAdminImpl(NodeAgentFactory nodeAgentFactory, Metrics metrics, Timer timer, FileSystem fileSystem) {
        this(nodeAgentContext -> create(timer, nodeAgentFactory, nodeAgentContext),
                metrics, timer, NODE_AGENT_FREEZE_TIMEOUT, NODE_AGENT_SPREAD, new ProcMeminfoReader(fileSystem));
    }

    public NodeAdminImpl(NodeAgentFactory nodeAgentFactory, Metrics metrics,
                         Timer timer, Duration freezeTimeout, Duration spread, ProcMeminfoReader procMeminfoReader) {
        this(nodeAgentContext -> create(timer, nodeAgentFactory, nodeAgentContext),
                metrics, timer, freezeTimeout, spread, procMeminfoReader);
    }

    NodeAdminImpl(NodeAgentWithSchedulerFactory nodeAgentWithSchedulerFactory,
                  Metrics metrics, Timer timer, Duration freezeTimeout, Duration spread,
                  ProcMeminfoReader procMeminfoReader) {
        this.nodeAgentWithSchedulerFactory = nodeAgentWithSchedulerFactory;
        this.timer = timer;
        this.freezeTimeout = freezeTimeout;
        this.spread = spread;
        this.previousWantFrozen = true;
        this.isFrozen = true;
        this.startOfFreezeConvergence = timer.currentTime();

        this.numberOfUnhandledExceptions = metrics.declareCounter("unhandled_exceptions",
                new Dimensions(Map.of("src", "node-agents")));

        this.procMeminfoReader = procMeminfoReader;
        this.jvmHeapUsed = metrics.declareGauge("mem.heap.used");
        this.jvmHeapFree = metrics.declareGauge("mem.heap.free");
        this.jvmHeapTotal = metrics.declareGauge("mem.heap.total");
        this.containerCount = metrics.declareGauge("container.count");
        this.metrics = metrics;
    }

    @Override
    public void refreshContainersToRun(Set<NodeAgentContext> nodeAgentContexts) {
        Map<String, NodeAgentContext> nodeAgentContextsByHostname = nodeAgentContexts.stream()
                .collect(Collectors.toMap(ctx -> ctx.node().id(), Function.identity()));

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
        Instant nextAgentStart = timer.currentTime();
        // At this point, nodeAgentContextsByHostname and nodeAgentWithSchedulerByHostname should have the same keys
        for (Map.Entry<String, NodeAgentContext> entry : nodeAgentContextsByHostname.entrySet()) {
            nodeAgentWithSchedulerByHostname.get(entry.getKey()).scheduleTickWith(entry.getValue(), nextAgentStart);
            nextAgentStart = nextAgentStart.plus(timeBetweenNodeAgents);
        }
    }

    @Override
    public void updateMetrics(boolean isSuspended) {
        int numContainers = 0;
        long totalContainerMemoryBytes = 0;

        for (NodeAgentWithScheduler nodeAgentWithScheduler : nodeAgentWithSchedulerByHostname.values()) {
            int count = nodeAgentWithScheduler.getAndResetNumberOfUnhandledExceptions();
            if (!isSuspended) numberOfUnhandledExceptions.add(count);
            Optional<ContainerStats> containerStats = nodeAgentWithScheduler.updateContainerNodeMetrics(isSuspended);
            if (containerStats.isPresent()) {
                ++numContainers;
                totalContainerMemoryBytes += containerStats.get().memoryStats().usage();
            }
        }

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long usedMemory = totalMemory - freeMemory;
        jvmHeapFree.sample(freeMemory);
        jvmHeapUsed.sample(usedMemory);
        jvmHeapTotal.sample(totalMemory);

        // No container stats are found while suspended, so skip setting these if so.
        if (!isSuspended) {
            containerCount.sample(numContainers);
            ProcMeminfo meminfo = procMeminfoReader.read();
            updateMemoryOverheadMetric(numContainers, meminfo.memTotalBytes() - meminfo.memAvailableBytes() - totalContainerMemoryBytes);
        }
    }

    private void updateMemoryOverheadMetric(int numContainers, double memoryOverhead) {
        final String name = "mem.system.overhead";
        Dimensions dimensions = new Dimensions(Map.of("containers", Integer.toString(numContainers)));
        metrics.declareGauge(Metrics.APPLICATION_HOST, name, dimensions, Metrics.DimensionType.DEFAULT)
               .sample(memoryOverhead);
        if (previousMemoryOverheadDimensions != null && !previousMemoryOverheadDimensions.equals(dimensions))
            metrics.deleteMetricByDimension(name, previousMemoryOverheadDimensions, Metrics.DimensionType.DEFAULT);
        previousMemoryOverheadDimensions = dimensions;
    }

    @Override
    public boolean setFrozen(boolean wantFrozen) {
        if (wantFrozen != previousWantFrozen) {
            if (wantFrozen) {
                this.startOfFreezeConvergence = timer.currentTime();
            } else {
                this.startOfFreezeConvergence = null;
            }

            previousWantFrozen = wantFrozen;
        }

        // Use filter with count instead of allMatch() because allMatch() will short circuit on first non-match
        boolean allNodeAgentsConverged = parallelStreamOfNodeAgentWithScheduler()
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
            return Duration.between(startOfFreezeConvergence, timer.currentTime());
        }
    }

    @Override
    public void stopNodeAgentServices() {
        // Each container may spend 1-1:30 minutes stopping
        parallelStreamOfNodeAgentWithScheduler().forEach(NodeAgentWithScheduler::stopForHostSuspension);
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        // Stop all node-agents in parallel, will block until the last NodeAgent is stopped
        parallelStreamOfNodeAgentWithScheduler().forEach(NodeAgentWithScheduler::stopForRemoval);
    }

    /**
     * Returns a parallel stream of NodeAgentWithScheduler.
     *
     * <p>Why not just call nodeAgentWithSchedulerByHostname.values().parallelStream()? Experiments
     * with Java 11 have shown that with 10 nodes and forEach(), there are a maximum of 3 concurrent
     * threads. With HashMap it produces 5.  With List it produces 10 concurrent threads.</p>
     */
    private Stream<NodeAgentWithScheduler> parallelStreamOfNodeAgentWithScheduler() {
        return List.copyOf(nodeAgentWithSchedulerByHostname.values()).parallelStream();
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
        Optional<ContainerStats> updateContainerNodeMetrics(boolean isSuspended) { return nodeAgent.updateContainerNodeMetrics(currentContext(), isSuspended); }
        int getAndResetNumberOfUnhandledExceptions() { return nodeAgent.getAndResetNumberOfUnhandledExceptions(); }

        @Override public void scheduleTickWith(NodeAgentContext context, Instant at) { nodeAgentScheduler.scheduleTickWith(context, at); }
        @Override public boolean setFrozen(boolean frozen, Duration timeout) { return nodeAgentScheduler.setFrozen(frozen, timeout); }
        @Override public NodeAgentContext currentContext() { return nodeAgentScheduler.currentContext(); }
    }

    @FunctionalInterface
    interface NodeAgentWithSchedulerFactory {
        NodeAgentWithScheduler create(NodeAgentContext context);
    }

    private static NodeAgentWithScheduler create(Timer timer, NodeAgentFactory nodeAgentFactory, NodeAgentContext context) {
        NodeAgentContextManager contextManager = new NodeAgentContextManager(timer, context);
        NodeAgent nodeAgent = nodeAgentFactory.create(contextManager, context);
        return new NodeAgentWithScheduler(nodeAgent, contextManager);
    }
}
