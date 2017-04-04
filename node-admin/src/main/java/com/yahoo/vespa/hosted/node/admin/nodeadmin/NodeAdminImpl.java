// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.collections.Pair;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.metrics.CounterWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.GaugeWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

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
    private final ScheduledExecutorService aclScheduler = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService metricsScheduler = Executors.newScheduledThreadPool(1);

    private final DockerOperations dockerOperations;
    private final Function<String, NodeAgent> nodeAgentFactory;
    private final Optional<StorageMaintainer> storageMaintainer;
    private boolean isFrozen = false;

    private final Map<String, NodeAgent> nodeAgents = new ConcurrentHashMap<>();

    private final int nodeAgentScanIntervalMillis;

    private final GaugeWrapper numberOfContainersInLoadImageState;
    private final CounterWrapper numberOfUnhandledExceptionsInNodeAgent;

    public NodeAdminImpl(final DockerOperations dockerOperations, final Function<String, NodeAgent> nodeAgentFactory,
                         final Optional<StorageMaintainer> storageMaintainer, final int nodeAgentScanIntervalMillis,
                         final MetricReceiverWrapper metricReceiver, final Optional<AclMaintainer> aclMaintainer) {
        this.dockerOperations = dockerOperations;
        this.nodeAgentFactory = nodeAgentFactory;
        this.storageMaintainer = storageMaintainer;
        this.nodeAgentScanIntervalMillis = nodeAgentScanIntervalMillis;

        Dimensions dimensions = new Dimensions.Builder()
                .add("host", HostName.getLocalhost())
                .add("role", "docker").build();

        this.numberOfContainersInLoadImageState = metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.image.loading");
        this.numberOfUnhandledExceptionsInNodeAgent = metricReceiver.declareCounter(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.unhandled_exceptions");

        metricsScheduler.scheduleAtFixedRate(() -> {
            try {
                nodeAgents.values().forEach(nodeAgent -> nodeAgent.updateContainerNodeMetrics(nodeAgents.size()));
            } catch (Throwable e) {
                logger.warning("Metric fetcher scheduler failed", e);
            }
        }, 0, 30, TimeUnit.SECONDS);

        aclMaintainer.ifPresent(maintainer -> aclScheduler.scheduleAtFixedRate(() -> {
            if (!isFrozen()) maintainer.run();
        }, 30, 60, TimeUnit.SECONDS));
    }

    @Override
    public void refreshContainersToRun(final List<ContainerNodeSpec> containersToRun) {
        final List<Container> existingContainers = dockerOperations.getAllManagedContainers();

        storageMaintainer.ifPresent(StorageMaintainer::cleanNodeAdmin);
        synchronizeNodeSpecsToNodeAgents(containersToRun, existingContainers);
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
        // Use filter with count instead of allMatch() because allMatch() will short curcuit on first non-match
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
    public void stopNodeAgentServices(List<String> hostnames) {
        nodeAgents.values().stream()
                .filter(nodeAgent -> hostnames.contains(nodeAgent.getHostname()))
                .forEach(NodeAgent::stopServices);
    }

    public Set<String> getListOfHosts() {
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
    public void shutdown() {
        metricsScheduler.shutdown();
        aclScheduler.shutdown();
        try {
            boolean metricsSchedulerShutdown = metricsScheduler.awaitTermination(30, TimeUnit.SECONDS);
            boolean aclSchedulerShutdown = aclScheduler.awaitTermination(30, TimeUnit.SECONDS);
            if (! (metricsSchedulerShutdown && aclSchedulerShutdown)) {
                throw new RuntimeException("Failed shuttingdown all scheduler(s), shutdown status:\n" +
                        "\tMetrics Scheduler: " + metricsSchedulerShutdown + "\n" +
                        "\tACL Scheduler: " + aclSchedulerShutdown);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (NodeAgent nodeAgent : nodeAgents.values()) {
            nodeAgent.stop();
        }
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

    // TODO This method should rather take a list of Hostname instead of Container. However, it triggers
    // a refactoring of the logic. Which is hard due to the style of programming.
    // The method streams the list of containers twice.
    void synchronizeNodeSpecsToNodeAgents(
            final List<ContainerNodeSpec> containersToRun,
            final List<Container> existingContainers) {
        final Stream<Pair<Optional<ContainerNodeSpec>, Optional<Container>>> nodeSpecContainerPairs = fullOuterJoin(
                containersToRun.stream(), nodeSpec -> nodeSpec.hostname,
                existingContainers.stream(), container -> container.hostname);

        final Set<String> nodeHostNames = containersToRun.stream()
                .map(spec -> spec.hostname)
                .collect(Collectors.toSet());
        final Set<String> obsoleteAgentHostNames = diff(nodeAgents.keySet(), nodeHostNames);
        obsoleteAgentHostNames.forEach(hostName -> nodeAgents.remove(hostName).stop());

        nodeSpecContainerPairs.forEach(nodeSpecContainerPair -> {
            final Optional<ContainerNodeSpec> nodeSpec = nodeSpecContainerPair.getFirst();
            final Optional<Container> existingContainer = nodeSpecContainerPair.getSecond();

            if (!nodeSpec.isPresent()) {
                assert existingContainer.isPresent();
                logger.warning("Container " + existingContainer.get() + " exists, but is not in node repository runlist");
                return;
            }

            ensureNodeAgentForNodeIsStarted(nodeSpec.get());
        });
    }

    private void ensureNodeAgentForNodeIsStarted(final ContainerNodeSpec nodeSpec) {
        if (nodeAgents.containsKey(nodeSpec.hostname)) {
            return;
        }

        final NodeAgent agent = nodeAgentFactory.apply(nodeSpec.hostname);
        agent.start(nodeAgentScanIntervalMillis);
        nodeAgents.put(nodeSpec.hostname, agent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.info("Interrupted while waiting between starting node-agents");
        }
    }
}
