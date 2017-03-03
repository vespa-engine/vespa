// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.collections.Pair;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
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
import com.yahoo.vespa.hosted.provision.Node;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final DockerOperations dockerOperations;
    private final Function<String, NodeAgent> nodeAgentFactory;
    private final Optional<StorageMaintainer> storageMaintainer;
    private final Optional<AclMaintainer> aclMaintainer;
    private AtomicBoolean frozen = new AtomicBoolean(false);

    private final Map<String, NodeAgent> nodeAgents = new HashMap<>();

    private final int nodeAgentScanIntervalMillis;

    private GaugeWrapper numberOfContainersInActiveState;
    private GaugeWrapper numberOfContainersInLoadImageState;
    private CounterWrapper numberOfUnhandledExceptionsInNodeAgent;

    public NodeAdminImpl(final DockerOperations dockerOperations, final Function<String, NodeAgent> nodeAgentFactory,
                         final Optional<StorageMaintainer> storageMaintainer, final int nodeAgentScanIntervalMillis,
                         final MetricReceiverWrapper metricReceiver, final Optional<AclMaintainer> aclMaintainer) {
        this.dockerOperations = dockerOperations;
        this.nodeAgentFactory = nodeAgentFactory;
        this.storageMaintainer = storageMaintainer;
        this.aclMaintainer = aclMaintainer;
        this.nodeAgentScanIntervalMillis = nodeAgentScanIntervalMillis;

        Dimensions dimensions = new Dimensions.Builder()
                .add("host", HostName.getLocalhost())
                .add("role", "docker").build();

        this.numberOfContainersInActiveState = metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.state.active");
        this.numberOfContainersInLoadImageState = metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.image.loading");
        this.numberOfUnhandledExceptionsInNodeAgent = metricReceiver.declareCounter(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.unhandled_exceptions");

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                nodeAgents.values().forEach(NodeAgent::updateContainerNodeMetrics);
            } catch (Throwable e) {
                logger.warning("Metric fetcher scheduler failed", e);
            }
        }, 0, 30000, TimeUnit.MILLISECONDS);

        this.aclMaintainer.ifPresent(maintainer -> scheduler.scheduleAtFixedRate(maintainer, 30, 60, TimeUnit.SECONDS));
    }

    public void refreshContainersToRun(final List<ContainerNodeSpec> containersToRun) {
        final List<Container> existingContainers = dockerOperations.getAllManagedContainers();

        storageMaintainer.ifPresent(StorageMaintainer::cleanNodeAdmin);
        synchronizeNodeSpecsToNodeAgents(containersToRun, existingContainers);
        dockerOperations.deleteUnusedDockerImages();

        updateNodeAgentMetrics();
    }

    private void updateNodeAgentMetrics() {
        int numberContainersInActive = 0;
        int numberContainersWaitingImage = 0;
        int numberOfNewUnhandledExceptions = 0;

        for (NodeAgent nodeAgent : nodeAgents.values()) {
            Optional<ContainerNodeSpec> nodeSpec = nodeAgent.getContainerNodeSpec();
            if (nodeSpec.isPresent() && nodeSpec.get().nodeState == Node.State.active) numberContainersInActive++;
            if (nodeAgent.isDownloadingImage()) numberContainersWaitingImage++;
            numberOfNewUnhandledExceptions += nodeAgent.getAndResetNumberOfUnhandledExceptions();
        }

        numberOfContainersInActiveState.sample(numberContainersInActive);
        numberOfContainersInLoadImageState.sample(numberContainersWaitingImage);
        numberOfUnhandledExceptionsInNodeAgent.add(numberOfNewUnhandledExceptions);
    }

    public boolean freezeNodeAgentsAndCheckIfAllFrozen() {
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            // We could make this blocking, this could speed up the suspend call a bit, but not sure if it is
            // worth it (it could block the rest call for some time and might have implications).
            nodeAgent.freeze();
        }
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            if (! nodeAgent.isFrozen()) {
                return false;
            }
        }
        return true;
    }

    public void unfreezeNodeAgents() {
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            nodeAgent.unfreeze();
        }
    }

    public boolean isFrozen() {
        return frozen.get();
    }

    public void setFrozen(boolean frozen) {
        this.frozen.set(frozen);
    }

    @Override
    public Optional<String> stopServices(List<String> nodes) {
        if ( ! isFrozen()) {
            return Optional.of("Node admin is not frozen");
        }
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            try {
                if (nodes.contains(nodeAgent.getHostname())) {
                    final ContainerName containerName = nodeAgent.getContainerName();

                    if ( ! isFrozen()) return Optional.of("Node agent for " + containerName + " is not frozen");

                    nodeAgent.stopServices(containerName);
                }
            } catch (Exception e) {
                return Optional.of(e.getMessage());
            }
        }
        return Optional.empty();
    }

    public Set<String> getListOfHosts() {
        return nodeAgents.keySet();
    }

    @Override
    public Map<String, Object> debugInfo() {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("isFrozen", frozen);

        List<Map<String, Object>> nodeAgentDebugs = nodeAgents.entrySet().stream()
                .map(node -> node.getValue().debugInfo()).collect(Collectors.toList());
        debug.put("NodeAgents", nodeAgentDebugs);
        return debug;
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (! scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Did not manage to shutdown node-agent metrics update metricsFetcherScheduler.");
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
    }
}
