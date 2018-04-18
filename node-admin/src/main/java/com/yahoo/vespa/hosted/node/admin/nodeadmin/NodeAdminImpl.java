// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.collections.Pair;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.metrics.CounterWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.GaugeWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Administers a host (for now only docker hosts) and its nodes (docker containers nodes).
 *
 * @author stiankri
 */
public class NodeAdminImpl implements NodeAdmin {
    private static final PrefixLogger logger = PrefixLogger.getNodeAdminLogger(NodeAdmin.class);
    private final ScheduledExecutorService aclScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("aclscheduler"));
    private final ScheduledExecutorService metricsScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("metricsscheduler"));

    private final DockerOperations dockerOperations;
    private final Function<String, NodeAgent> nodeAgentFactory;
    private final StorageMaintainer storageMaintainer;
    private final Runnable aclMaintainer;

    private final Clock clock;
    private boolean previousWantFrozen;
    private boolean isFrozen;
    private Instant startOfFreezeConvergence;

    private final Map<ContainerName, NodeAgent> nodeAgents = new ConcurrentHashMap<>();

    private final GaugeWrapper numberOfContainersInLoadImageState;
    private final CounterWrapper numberOfUnhandledExceptionsInNodeAgent;

    public NodeAdminImpl(final DockerOperations dockerOperations,
                         final Function<String, NodeAgent> nodeAgentFactory,
                         final StorageMaintainer storageMaintainer,
                         final Runnable aclMaintainer,
                         final MetricReceiverWrapper metricReceiver,
                         final Clock clock) {
        this.dockerOperations = dockerOperations;
        this.nodeAgentFactory = nodeAgentFactory;
        this.storageMaintainer = storageMaintainer;
        this.aclMaintainer = aclMaintainer;

        this.clock = clock;
        this.previousWantFrozen = true;
        this.isFrozen = true;
        this.startOfFreezeConvergence = clock.instant();

        Dimensions dimensions = new Dimensions.Builder().add("role", "docker").build();
        this.numberOfContainersInLoadImageState = metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.image.loading");
        this.numberOfUnhandledExceptionsInNodeAgent = metricReceiver.declareCounter(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.unhandled_exceptions");
    }

    @Override
    public void refreshContainersToRun(final List<NodeSpec> containersToRun) {
        final List<ContainerName> existingContainerNames = dockerOperations.listAllManagedContainers();
        final List<String> containersToRunHostnames = containersToRun.stream()
                .map(container -> container.hostname)
                .collect(Collectors.toList());

        storageMaintainer.cleanNodeAdmin();
        synchronizeNodesToNodeAgents(containersToRunHostnames, existingContainerNames);
        dockerOperations.deleteUnusedDockerImages();

        updateNodeAgentMetrics();
    }

    private void updateNodeAgentMetrics() {
        int numberContainersWaitingImage = 0;
        int numberOfNewUnhandledExceptions = 0;

        for (NodeAgent nodeAgent : nodeAgents.values()) {
            if (nodeAgent.isDownloadingImage()) numberContainersWaitingImage++;
            numberOfNewUnhandledExceptions += nodeAgent.getAndResetNumberOfUnhandledExceptions();
        }

        numberOfContainersInLoadImageState.sample(numberContainersWaitingImage);
        numberOfUnhandledExceptionsInNodeAgent.add(numberOfNewUnhandledExceptions);
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
        boolean allNodeAgentsConverged = nodeAgents.values().stream()
                .filter(nodeAgent -> !nodeAgent.setFrozen(wantFrozen))
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
            return Duration.ofSeconds(0);
        } else {
            return Duration.between(startOfFreezeConvergence, clock.instant());
        }
    }

    @Override
    public void stopNodeAgentServices(List<String> hostnames) {
        // Each container may spend 1-1:30 minutes stopping
        nodeAgents.values().parallelStream()
                .filter(nodeAgent -> hostnames.contains(nodeAgent.getHostname()))
                .forEach(NodeAgent::stopServices);
    }

    @Override
    public Set<ContainerName> getListOfHosts() {
        return nodeAgents.keySet();
    }

    @Override
    public Map<String, Object> debugInfo() {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("isFrozen", isFrozen);

        List<Map<String, Object>> nodeAgentDebugs = nodeAgents.entrySet().stream()
                .map(node -> node.getValue().debugInfo()).collect(Collectors.toList());
        debug.put("NodeAgents", nodeAgentDebugs);
        return debug;
    }

    @Override
    public void start() {
        metricsScheduler.scheduleAtFixedRate(() -> {
            try {
                nodeAgents.values().forEach(NodeAgent::updateContainerNodeMetrics);
            } catch (Throwable e) {
                logger.warning("Metric fetcher scheduler failed", e);
            }
        }, 0, 55, TimeUnit.SECONDS);

        int delay = 120; // WARNING: Reducing this will increase the load on config servers.
        aclScheduler.scheduleWithFixedDelay(() -> {
            if (!isFrozen()) aclMaintainer.run();
        }, 30, delay, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        metricsScheduler.shutdown();
        aclScheduler.shutdown();

        // Stop all node-agents in parallel, will block until the last NodeAgent is stopped
        nodeAgents.values().parallelStream().forEach(NodeAgent::stop);

        do {
            try {
                metricsScheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                aclScheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                logger.info("Was interrupted while waiting for metricsScheduler and aclScheduler to shutdown");
            }
        } while (!metricsScheduler.isTerminated() || !aclScheduler.isTerminated());
    }

    // Set-difference. Returns minuend minus subtrahend.
    private static <T> Set<T> diff(final Set<T> minuend, final Set<T> subtrahend) {
        final HashSet<T> result = new HashSet<>(minuend);
        result.removeAll(subtrahend);
        return result;
    }

    // Returns a full outer join of two data sources (of types T and U) on some extractable attribute (of type V).
    // Full outer join means that all elements of both data sources are included in the result,
    // even when there is no corresponding element (having the same attribute) in the other data set,
    // in which case the value from the other source will be empty.
    static <T, U, V> Stream<Pair<Optional<T>, Optional<U>>> fullOuterJoin(
            final Stream<T> tStream, final Function<T, V> tAttributeExtractor,
            final Stream<U> uStream, final Function<U, V> uAttributeExtractor) {
        final Map<V, T> tMap = tStream.collect(Collectors.toMap(tAttributeExtractor, t -> t));
        final Map<V, U> uMap = uStream.collect(Collectors.toMap(uAttributeExtractor, u -> u));
        return Stream.concat(tMap.keySet().stream(), uMap.keySet().stream())
                .distinct()
                .map(key -> new Pair<>(Optional.ofNullable(tMap.get(key)), Optional.ofNullable(uMap.get(key))));
    }

    // The method streams the list of containers twice.
    void synchronizeNodesToNodeAgents(
            final List<String> hostnamesToRun,
            final List<ContainerName> existingContainers) {
        final Map<ContainerName, String> hostnameByContainerName = hostnamesToRun.stream()
                .collect(Collectors.toMap(ContainerName::fromHostname, i -> i));
        final Stream<Pair<Optional<ContainerName>, Optional<ContainerName>>> nodeSpecContainerPairs = fullOuterJoin(
                hostnameByContainerName.keySet().stream(), containerName -> containerName,
                existingContainers.stream(), containerName -> containerName);

        final Set<ContainerName> obsoleteAgentContainerNames = diff(nodeAgents.keySet(), new HashSet<>(hostnameByContainerName.keySet()));
        obsoleteAgentContainerNames.forEach(containerName -> nodeAgents.remove(containerName).stop());

        nodeSpecContainerPairs.forEach(nodeSpecContainerPair -> {
            final Optional<ContainerName> nodeSpec = nodeSpecContainerPair.getFirst();
            final Optional<ContainerName> existingContainer = nodeSpecContainerPair.getSecond();

            if (!nodeSpec.isPresent()) {
                assert existingContainer.isPresent();
                logger.warning("Container " + existingContainer.get().asString() + " exists, but is not in node repository runlist");
                return;
            }

            ensureNodeAgentForNodeIsStarted(nodeSpec.get(), hostnameByContainerName.get(nodeSpec.get()));
        });
    }

    private void ensureNodeAgentForNodeIsStarted(ContainerName containerName, String hostname) {
        if (nodeAgents.containsKey(containerName)) {
            return;
        }

        final NodeAgent agent = nodeAgentFactory.apply(hostname);
        agent.start();
        nodeAgents.put(containerName, agent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.info("Interrupted while waiting between starting node-agents");
        }
    }
}
