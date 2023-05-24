// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import ai.vespa.metrics.ConfigServerMetrics;
import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.curator.stats.LatencyMetrics;
import com.yahoo.vespa.curator.stats.LockStats;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import com.yahoo.vespa.hosted.provision.persistence.CacheStats;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.any;

/**
 * @author oyving
 */
public class MetricsReporter extends NodeRepositoryMaintainer {

    private final Set<Pair<Metric.Context, String>> nonZeroMetrics = new HashSet<>();
    private final Metric metric;
    private final ServiceMonitor serviceMonitor;
    private final Map<Map<String, String>, Metric.Context> contextMap = new HashMap<>();
    private final Supplier<Integer> pendingRedeploymentsSupplier;

    MetricsReporter(NodeRepository nodeRepository,
                    Metric metric,
                    ServiceMonitor serviceMonitor,
                    Supplier<Integer> pendingRedeploymentsSupplier,
                    Duration interval) {
        super(nodeRepository, interval, metric);
        this.metric = metric;
        this.serviceMonitor = serviceMonitor;
        this.pendingRedeploymentsSupplier = pendingRedeploymentsSupplier;
    }

    @Override
    public double maintain() {
        // Sort by hostname to get deterministic metric reporting order (and hopefully avoid changes
        // to metric reporting time so we get double reporting or no reporting within a minute)
        NodeList nodes = nodeRepository().nodes().list().sortedBy(Comparator.comparing(Node::hostname));
        ServiceModel serviceModel = serviceMonitor.getServiceModelSnapshot();

        updateZoneMetrics();
        updateCacheMetrics();
        updateMaintenanceMetrics();
        nodes.forEach(node -> updateNodeMetrics(node, serviceModel));
        updateNodeCountMetrics(nodes);
        updateLockMetrics();
        updateContainerMetrics(nodes);
        updateTenantUsageMetrics(nodes);
        updateRepairTicketMetrics(nodes);
        updateAllocationMetrics(nodes);
        updateClusterMetrics(nodes);
        return 1.0;
    }

    private void updateAllocationMetrics(NodeList nodes) {
        Map<ClusterId, List<Node>> byCluster = nodes.stream()
                                                    .filter(node -> node.allocation().isPresent())
                                                    .filter(node -> !node.allocation().get().owner().instance().isTester())
                                                    .collect(Collectors.groupingBy(node -> new ClusterId(node.allocation().get().owner(), node.allocation().get().membership().cluster().id())));
        byCluster.forEach((clusterId, allocatedNodes) -> {
            int activeNodes = 0;
            int nonActiveNodes = 0;
            for (var node : allocatedNodes) {
                if (node.state() == State.active) {
                    activeNodes++;
                } else {
                    nonActiveNodes++;
                }
            }
            double nonActiveFraction;
            if (activeNodes == 0) { // Cluster has been removed
                nonActiveFraction = 1;
            } else {
                nonActiveFraction = (double) nonActiveNodes / ((double) activeNodes + (double) nonActiveNodes);
            }
            Metric.Context context = getContext(dimensions(clusterId.application(), clusterId.cluster()));
            metric.set(ConfigServerMetrics.NODES_ACTIVE.baseName(), activeNodes, context);
            metric.set(ConfigServerMetrics.NODES_NON_ACTIVE.baseName(), nonActiveNodes, context);
            metric.set(ConfigServerMetrics.NODES_NON_ACTIVE_FRACTION.baseName(), nonActiveFraction, context);
        });
    }

    private void updateClusterMetrics(NodeList nodes) {
        Map<ClusterId, List<Node>> byCluster = nodes.stream()
                                                     .filter(node -> node.type() == NodeType.tenant)
                                                     .filter(node -> node.state() == State.active)
                                                     .filter(node -> node.allocation().isPresent())
                                                     .collect(Collectors.groupingBy(node -> new ClusterId(node.allocation().get().owner(), node.allocation().get().membership().cluster().id())));
        byCluster.forEach((clusterId, clusterNodes) -> {
            Metric.Context context = getContext(dimensions(clusterId.application(), clusterId.cluster()));
            updateExclusiveSwitchMetrics(clusterNodes, nodes, context);
            updateClusterCostMetrics(clusterId, clusterNodes, context);
        });
    }

    private void updateExclusiveSwitchMetrics(List<Node> clusterNodes, NodeList allNodes, Metric.Context context) {
        NodeList clusterHosts = allNodes.parentsOf(NodeList.copyOf(clusterNodes));
        long nodesOnExclusiveSwitch = NodeList.copyOf(clusterNodes).onExclusiveSwitch(clusterHosts).size();
        double exclusiveSwitchRatio = nodesOnExclusiveSwitch / (double) clusterNodes.size();
        metric.set(ConfigServerMetrics.NODES_EXCLUSIVE_SWITCH_FRACTION.baseName(), exclusiveSwitchRatio,context);
    }

    private void updateClusterCostMetrics(ClusterId clusterId,
                                          List<Node>  clusterNodes, Metric.Context context) {
        var cluster = nodeRepository().applications().get(clusterId.application())
                                      .flatMap(application -> application.cluster(clusterId.cluster()));
        if (cluster.isEmpty()) return;
        double cost = clusterNodes.stream().mapToDouble(node -> node.resources().cost()).sum();
        metric.set(ConfigServerMetrics.CLUSTER_COST.baseName(), cost, context);
        metric.set(ConfigServerMetrics.CLUSTER_LOAD_IDEAL_CPU.baseName(), cluster.get().target().ideal().cpu(), context);
        metric.set(ConfigServerMetrics.CLUSTER_LOAD_IDEAL_MEMORY.baseName(), cluster.get().target().ideal().memory(), context);
        metric.set(ConfigServerMetrics.CLUSTER_LOAD_IDEAL_DISK.baseName(), cluster.get().target().ideal().disk(), context);
    }

    private void updateZoneMetrics() {
        metric.set(ConfigServerMetrics.ZONE_WORKING.baseName(), nodeRepository().nodes().isWorking() ? 1 : 0, null);
    }

    private void updateCacheMetrics() {
        CacheStats nodeCacheStats = nodeRepository().database().nodeSerializerCacheStats();
        metric.set(ConfigServerMetrics.CACHE_NODE_OBJECT_HIT_RATE.baseName(), nodeCacheStats.hitRate(), null);
        metric.set(ConfigServerMetrics.CACHE_NODE_OBJECT_EVICTION_COUNT.baseName(), nodeCacheStats.evictionCount(), null);
        metric.set(ConfigServerMetrics.CACHE_NODE_OBJECT_SIZE.baseName(), nodeCacheStats.size(), null);

        CacheStats curatorCacheStats = nodeRepository().database().cacheStats();
        metric.set(ConfigServerMetrics.CACHE_CURATOR_HIT_RATE.baseName(), curatorCacheStats.hitRate(), null);
        metric.set(ConfigServerMetrics.CACHE_CURATOR_EVICTION_COUNT.baseName(), curatorCacheStats.evictionCount(), null);
        metric.set(ConfigServerMetrics.CACHE_CURATOR_SIZE.baseName(), curatorCacheStats.size(), null);
    }

    private void updateMaintenanceMetrics() {
        metric.set(ConfigServerMetrics.HOSTED_VESPA_PENDING_REDEPLOYMENTS.baseName(), pendingRedeploymentsSupplier.get(), null);
    }

    /**
     * NB: Keep this metric set in sync with internal configserver metric pre-aggregation
     */
    private void updateNodeMetrics(Node node, ServiceModel serviceModel) {
        Metric.Context context;

        Optional<Allocation> allocation = node.allocation();
        if (allocation.isPresent()) {
            ApplicationId applicationId = allocation.get().owner();
            Map<String, String> dimensions = new HashMap<>(dimensions(applicationId));
            dimensions.put("state", node.state().name());
            dimensions.put("host", node.hostname());
            dimensions.put("clustertype", allocation.get().membership().cluster().type().name());
            dimensions.put("clusterid", allocation.get().membership().cluster().id().value());
            context = getContext(dimensions);

            long wantedRestartGeneration = allocation.get().restartGeneration().wanted();
            metric.set(ConfigServerMetrics.WANTED_RESTART_GENERATION.baseName(), wantedRestartGeneration, context);
            long currentRestartGeneration = allocation.get().restartGeneration().current();
            metric.set(ConfigServerMetrics.CURRENT_RESTART_GENERATION.baseName(), currentRestartGeneration, context);
            boolean wantToRestart = currentRestartGeneration < wantedRestartGeneration;
            metric.set(ConfigServerMetrics.WANT_TO_RESTART.baseName(), wantToRestart ? 1 : 0, context);

            metric.set(ConfigServerMetrics.RETIRED.baseName(), allocation.get().membership().retired() ? 1 : 0, context);

            Version wantedVersion = allocation.get().membership().cluster().vespaVersion();
            double wantedVersionNumber = getVersionAsNumber(wantedVersion);
            metric.set(ConfigServerMetrics.WANTED_VESPA_VERSION.baseName(), wantedVersionNumber, context);

            Optional<Version> currentVersion = node.status().vespaVersion();
            boolean converged = currentVersion.isPresent() &&
                    currentVersion.get().equals(wantedVersion);
            metric.set(ConfigServerMetrics.WANT_TO_CHANGE_VESPA_VERSION.baseName(), converged ? 0 : 1, context);
            if (node.cloudAccount().isEnclave(nodeRepository().zone())) {
                metric.set(ConfigServerMetrics.HAS_WIRE_GUARD_KEY.baseName(), node.wireguardPubKey().isPresent() ? 1 : 0, context);
            }
        } else {
            context = getContext(Map.of("state", node.state().name(),
                                        "host", node.hostname()));
        }

        Optional<Version> currentVersion = node.status().vespaVersion();
        if (currentVersion.isPresent()) {
            double currentVersionNumber = getVersionAsNumber(currentVersion.get());
            metric.set(ConfigServerMetrics.CURRENT_VESPA_VERSION.baseName(), currentVersionNumber, context);
        }

        long wantedRebootGeneration = node.status().reboot().wanted();
        metric.set(ConfigServerMetrics.WANTED_REBOOT_GENERATION.baseName(), wantedRebootGeneration, context);
        long currentRebootGeneration = node.status().reboot().current();
        metric.set(ConfigServerMetrics.CURRENT_REBOOT_GENERATION.baseName(), currentRebootGeneration, context);
        boolean wantToReboot = currentRebootGeneration < wantedRebootGeneration;
        metric.set(ConfigServerMetrics.WANT_TO_REBOOT.baseName(), wantToReboot ? 1 : 0, context);

        metric.set(ConfigServerMetrics.WANT_TO_RETIRE.baseName(), node.status().wantToRetire() ? 1 : 0, context);
        metric.set(ConfigServerMetrics.WANT_TO_DEPROVISION.baseName(), node.status().wantToDeprovision() ? 1 : 0, context);
        metric.set(ConfigServerMetrics.FAIL_REPORT.baseName(), NodeFailer.reasonsToFailHost(node).isEmpty() ? 0 : 1, context);

        HostName hostname = new HostName(node.hostname());

        serviceModel.getApplication(hostname)
                .map(ApplicationInstance::reference)
                .map(reference -> nodeRepository().orchestrator().getHostInfo(reference, hostname))
                .ifPresent(info -> {
                    int suspended = info.status().isSuspended() ? 1 : 0;
                    metric.set(ConfigServerMetrics.SUSPENDED.baseName(), suspended, context);
                    long suspendedSeconds = info.suspendedSince()
                            .map(suspendedSince -> Duration.between(suspendedSince, clock().instant()).getSeconds())
                            .orElse(0L);
                    metric.set(ConfigServerMetrics.SUSPENDED_SECONDS.baseName(), suspendedSeconds, context);
                });

        long numberOfServices;
        List<ServiceInstance> services = serviceModel.getServiceInstancesByHostName().get(hostname);
        if (services == null) {
            numberOfServices = 0;
        } else {
            Map<ServiceStatus, Long> servicesCount = services.stream().collect(
                    Collectors.groupingBy(ServiceInstance::serviceStatus, Collectors.counting()));

            numberOfServices = servicesCount.values().stream().mapToLong(Long::longValue).sum();

            metric.set(
                    ConfigServerMetrics.NUMBER_OF_SERVICES_UP.baseName(),
                    servicesCount.getOrDefault(ServiceStatus.UP, 0L),
                    context);

            metric.set(
                    ConfigServerMetrics.NUMBER_OF_SERVICES_NOT_CHECKED.baseName(),
                    servicesCount.getOrDefault(ServiceStatus.NOT_CHECKED, 0L),
                    context);

            long numberOfServicesDown = servicesCount.getOrDefault(ServiceStatus.DOWN, 0L);
            metric.set(ConfigServerMetrics.NUMBER_OF_SERVICES_DOWN.baseName(), numberOfServicesDown, context);

            metric.set(ConfigServerMetrics.SOME_SERVICES_DOWN.baseName(), (numberOfServicesDown > 0 ? 1 : 0), context);

            metric.set(ConfigServerMetrics.NUMBER_OF_SERVICES_UNKNOWN.baseName(), servicesCount.getOrDefault(ServiceStatus.UNKNOWN, 0L), context);

            boolean down = NodeHealthTracker.allDown(services);
            metric.set(ConfigServerMetrics.NODE_FAILER_BAD_NODE.baseName(), (down ? 1 : 0), context);

            boolean nodeDownInNodeRepo = node.isDown();
            metric.set(ConfigServerMetrics.DOWN_IN_NODE_REPO.baseName(), (nodeDownInNodeRepo ? 1 : 0), context);
        }

        metric.set(ConfigServerMetrics.NUMBER_OF_SERVICES.baseName(), numberOfServices, context);
    }

    private static String toApp(ApplicationId applicationId) {
        return applicationId.application().value() + "." + applicationId.instance().value();
    }

    /**
     * A version 6.163.20 will be returned as a number 163.020. The major
     * version can normally be inferred. As long as the micro version stays
     * below 1000 these numbers sort like Version.
     */
    private static double getVersionAsNumber(Version version) {
        return version.getMinor() + version.getMicro() / 1000.0;
    }

    private Metric.Context getContext(Map<String, String> dimensions) {
        return contextMap.computeIfAbsent(dimensions, metric::createContext);
    }

    private void updateNodeCountMetrics(NodeList nodes) {
        var nodesByState = nodes.nodeType(NodeType.tenant)
                .asList().stream()
                .collect(Collectors.groupingBy(Node::state));

        var hostsByState = nodes.nodeType(NodeType.host)
                .asList().stream()
                .collect(Collectors.groupingBy(Node::state));

        // Count per state
        for (State state : State.values()) {
            var nodesInState = nodesByState.getOrDefault(state, List.of());
            var hostsInState = hostsByState.getOrDefault(state, List.of());
            metric.set("hostedVespa." + state.name() + "Nodes", nodesInState.size(), null);
            metric.set("hostedVespa." + state.name() + "Hosts", hostsInState.size(), null);
        }
    }

    private void updateLockMetrics() {
        LockStats.getGlobal().getLockMetricsByPath()
                .forEach((lockPath, lockMetrics) -> {
                    Metric.Context context = getContext(Map.of("lockPath", lockPath));

                    LatencyMetrics acquireLatencyMetrics = lockMetrics.getAndResetAcquireLatencyMetrics();
                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_ACQUIRE_MAX_ACTIVE_LATENCY.baseName(), acquireLatencyMetrics.maxActiveLatencySeconds(), context);
                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_ACQUIRE_HZ.baseName(), acquireLatencyMetrics.startHz(), context);
                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_ACQUIRE_LOAD.baseName(), acquireLatencyMetrics.load(), context);

                    LatencyMetrics lockedLatencyMetrics = lockMetrics.getAndResetLockedLatencyMetrics();
                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_LOCKED_LATENCY.baseName(), lockedLatencyMetrics.maxLatencySeconds(), context);
                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_LOCKED_LOAD.baseName(), lockedLatencyMetrics.load(), context);

                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_ACQUIRE_TIMED_OUT.baseName(), lockMetrics.getAndResetAcquireTimedOutCount(), context);
                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_DEADLOCK.baseName(), lockMetrics.getAndResetDeadlockCount(), context);

                    // bucket for various rare errors - to reduce #metrics
                    setNonZero(ConfigServerMetrics.LOCK_ATTEMPT_ERRORS.baseName(),
                            lockMetrics.getAndResetAcquireFailedCount() +
                                    lockMetrics.getAndResetReleaseFailedCount() +
                                    lockMetrics.getAndResetNakedReleaseCount() +
                                    lockMetrics.getAndResetAcquireWithoutReleaseCount() +
                                    lockMetrics.getAndResetForeignReleaseCount(),
                            context);
                });
    }

    private void setNonZero(String key, Number value, Metric.Context context) {
        var metricKey = new Pair<>(context, key);
        if (Double.compare(value.doubleValue(), 0.0) != 0) {
            metric.set(key, value, context);
            nonZeroMetrics.add(metricKey);
        } else if (nonZeroMetrics.remove(metricKey)) {
            // Need to set the metric to 0 after it has been set to non-zero, to avoid carrying
            // a non-zero 'last' from earlier periods.
            metric.set(key, value, context);
        }
    }

    private void updateContainerMetrics(NodeList nodes) {
        NodeResources totalCapacity = getCapacityTotal(nodes);
        metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_CPU.baseName(), totalCapacity.vcpu(), null);
        metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_MEM.baseName(), totalCapacity.memoryGb(), null);
        metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_DISK.baseName(), totalCapacity.diskGb(), null);

        NodeResources totalFreeCapacity = getFreeCapacityTotal(nodes);
        metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_CPU.baseName(), totalFreeCapacity.vcpu(), null);
        metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_MEM.baseName(), totalFreeCapacity.memoryGb(), null);
        metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_DISK.baseName(), totalFreeCapacity.diskGb(), null);
    }

    private void updateTenantUsageMetrics(NodeList nodes) {
        nodes.nodeType(NodeType.tenant).stream()
                .filter(node -> node.allocation().isPresent())
                .collect(Collectors.groupingBy(node -> node.allocation().get().owner()))
                .forEach(
                        (applicationId, applicationNodes) -> {
                            var allocatedCapacity = applicationNodes.stream()
                                    .map(node -> node.allocation().get().requestedResources().justNumbers())
                                    .reduce(new NodeResources(0, 0, 0, 0, any).justNumbers(), NodeResources::add);

                            var context = getContext(dimensions(applicationId));

                            metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_CPU.baseName(), allocatedCapacity.vcpu(), context);
                            metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_MEM.baseName(), allocatedCapacity.memoryGb(), context);
                            metric.set(ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_DISK.baseName(), allocatedCapacity.diskGb(), context);
                        }
                );
    }

    private void updateRepairTicketMetrics(NodeList nodes) {
        nodes.nodeType(NodeType.host).stream()
             .map(node -> node.reports().getReport("repairTicket"))
             .flatMap(Optional::stream)
             .map(report -> report.getInspector().field("status").asString())
             .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
             .forEach((status, number) -> metric.set(ConfigServerMetrics.HOSTED_VESPA_BREAKFIXED_HOSTS.baseName(), number, getContext(Map.of("status", status))));
    }

    static Map<String, String> dimensions(ApplicationId application, ClusterSpec.Id cluster) {
        Map<String, String> dimensions = new HashMap<>(dimensions(application));
        dimensions.put("clusterid", cluster.value());
        return dimensions;
    }

    private static Map<String, String> dimensions(ApplicationId application) {
        return Map.of("tenantName", application.tenant().value(),
                      "applicationId", application.serializedForm().replace(':', '.'),
                      "app", toApp(application));
    }

    private static NodeResources getCapacityTotal(NodeList nodes) {
        return nodes.hosts().state(State.active).asList().stream()
                    .map(host -> host.flavor().resources())
                    .map(NodeResources::justNumbers)
                    .reduce(new NodeResources(0, 0, 0, 0, any).justNumbers(), NodeResources::add);
    }

    private static NodeResources getFreeCapacityTotal(NodeList nodes) {
        return nodes.hosts().state(State.active).asList().stream()
                    .map(n -> freeCapacityOf(nodes, n))
                    .map(NodeResources::justNumbers)
                    .reduce(new NodeResources(0, 0, 0, 0, any).justNumbers(), NodeResources::add);
    }

    private static NodeResources freeCapacityOf(NodeList nodes, Node host) {
        return nodes.childrenOf(host).asList().stream()
                    .map(node -> node.flavor().resources().justNumbers())
                    .reduce(host.flavor().resources().justNumbers(), NodeResources::subtract);
    }

}
