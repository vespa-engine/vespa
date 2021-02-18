// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

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
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.persistence.CacheStats;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final Orchestrator orchestrator;
    private final ServiceMonitor serviceMonitor;
    private final Map<Map<String, String>, Metric.Context> contextMap = new HashMap<>();
    private final Supplier<Integer> pendingRedeploymentsSupplier;

    MetricsReporter(NodeRepository nodeRepository,
                    Metric metric,
                    Orchestrator orchestrator,
                    ServiceMonitor serviceMonitor,
                    Supplier<Integer> pendingRedeploymentsSupplier,
                    Duration interval) {
        super(nodeRepository, interval, metric);
        this.metric = metric;
        this.orchestrator = orchestrator;
        this.serviceMonitor = serviceMonitor;
        this.pendingRedeploymentsSupplier = pendingRedeploymentsSupplier;
    }

    @Override
    public boolean maintain() {
        NodeList nodes = nodeRepository().nodes().list();
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
        updateExclusiveSwitchMetrics(nodes);
        return true;
    }

    private void updateAllocationMetrics(NodeList nodes) {
        Map<ClusterKey, List<Node>> byCluster = nodes.stream()
                                                     .filter(node -> node.allocation().isPresent())
                                                     .filter(node -> !node.allocation().get().owner().instance().isTester())
                                                     .collect(Collectors.groupingBy(node -> new ClusterKey(node.allocation().get().owner(), node.allocation().get().membership().cluster().id())));
        byCluster.forEach((clusterKey, allocatedNodes) -> {
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
            Metric.Context context = getContext(dimensions(clusterKey.application, clusterKey.cluster));
            metric.set("nodes.active", activeNodes, context);
            metric.set("nodes.nonActive", nonActiveNodes, context);
            metric.set("nodes.nonActiveFraction", nonActiveFraction, context);
        });
    }

    private void updateExclusiveSwitchMetrics(NodeList nodes) {
        Map<ClusterKey, List<Node>> byCluster = nodes.stream()
                                                     .filter(node -> node.type() == NodeType.tenant)
                                                     .filter(node -> node.state() == State.active)
                                                     .filter(node -> node.allocation().isPresent())
                                                     .collect(Collectors.groupingBy(node -> new ClusterKey(node.allocation().get().owner(), node.allocation().get().membership().cluster().id())));
        byCluster.forEach((clusterKey, clusterNodes) -> {
            NodeList clusterHosts = nodes.parentsOf(NodeList.copyOf(clusterNodes));
            long nodesOnExclusiveSwitch = NodeList.copyOf(clusterNodes).onExclusiveSwitch(clusterHosts).size();
            double exclusiveSwitchRatio = nodesOnExclusiveSwitch / (double) clusterNodes.size();
            metric.set("nodes.exclusiveSwitchFraction", exclusiveSwitchRatio, getContext(dimensions(clusterKey.application, clusterKey.cluster)));
        });
    }

    private void updateZoneMetrics() {
        metric.set("zone.working", nodeRepository().nodes().isWorking() ? 1 : 0, null);
    }

    private void updateCacheMetrics() {
        CacheStats nodeCacheStats = nodeRepository().database().nodeSerializerCacheStats();
        metric.set("cache.nodeObject.hitRate", nodeCacheStats.hitRate(), null);
        metric.set("cache.nodeObject.evictionCount", nodeCacheStats.evictionCount(), null);
        metric.set("cache.nodeObject.size", nodeCacheStats.size(), null);

        CacheStats curatorCacheStats = nodeRepository().database().cacheStats();
        metric.set("cache.curator.hitRate", curatorCacheStats.hitRate(), null);
        metric.set("cache.curator.evictionCount", curatorCacheStats.evictionCount(), null);
        metric.set("cache.curator.size", curatorCacheStats.size(), null);
    }

    private void updateMaintenanceMetrics() {
        metric.set("hostedVespa.pendingRedeployments", pendingRedeploymentsSupplier.get(), null);
    }

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
            metric.set("wantedRestartGeneration", wantedRestartGeneration, context);
            long currentRestartGeneration = allocation.get().restartGeneration().current();
            metric.set("currentRestartGeneration", currentRestartGeneration, context);
            boolean wantToRestart = currentRestartGeneration < wantedRestartGeneration;
            metric.set("wantToRestart", wantToRestart ? 1 : 0, context);

            metric.set("retired", allocation.get().membership().retired() ? 1 : 0, context);

            Version wantedVersion = allocation.get().membership().cluster().vespaVersion();
            double wantedVersionNumber = getVersionAsNumber(wantedVersion);
            metric.set("wantedVespaVersion", wantedVersionNumber, context);

            Optional<Version> currentVersion = node.status().vespaVersion();
            boolean converged = currentVersion.isPresent() &&
                    currentVersion.get().equals(wantedVersion);
            metric.set("wantToChangeVespaVersion", converged ? 0 : 1, context);
        } else {
            context = getContext(Map.of("state", node.state().name(),
                                        "host", node.hostname()));
        }

        Optional<Version> currentVersion = node.status().vespaVersion();
        if (currentVersion.isPresent()) {
            double currentVersionNumber = getVersionAsNumber(currentVersion.get());
            metric.set("currentVespaVersion", currentVersionNumber, context);
        }

        long wantedRebootGeneration = node.status().reboot().wanted();
        metric.set("wantedRebootGeneration", wantedRebootGeneration, context);
        long currentRebootGeneration = node.status().reboot().current();
        metric.set("currentRebootGeneration", currentRebootGeneration, context);
        boolean wantToReboot = currentRebootGeneration < wantedRebootGeneration;
        metric.set("wantToReboot", wantToReboot ? 1 : 0, context);

        metric.set("wantToRetire", node.status().wantToRetire() ? 1 : 0, context);
        metric.set("wantToDeprovision", node.status().wantToDeprovision() ? 1 : 0, context);
        metric.set("failReport", NodeFailer.reasonsToFailParentHost(node).isEmpty() ? 0 : 1, context);

        HostName hostname = new HostName(node.hostname());

        serviceModel.getApplication(hostname)
                .map(ApplicationInstance::reference)
                .map(reference -> orchestrator.getHostInfo(reference, hostname))
                .ifPresent(info -> {
                    int suspended = info.status().isSuspended() ? 1 : 0;
                    metric.set("suspended", suspended, context);
                    metric.set("allowedToBeDown", suspended, context); // remove summer 2020.
                    long suspendedSeconds = info.suspendedSince()
                            .map(suspendedSince -> Duration.between(suspendedSince, clock().instant()).getSeconds())
                            .orElse(0L);
                    metric.set("suspendedSeconds", suspendedSeconds, context);
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
                    "numberOfServicesUp",
                    servicesCount.getOrDefault(ServiceStatus.UP, 0L),
                    context);

            metric.set(
                    "numberOfServicesNotChecked",
                    servicesCount.getOrDefault(ServiceStatus.NOT_CHECKED, 0L),
                    context);

            long numberOfServicesDown = servicesCount.getOrDefault(ServiceStatus.DOWN, 0L);
            metric.set("numberOfServicesDown", numberOfServicesDown, context);

            metric.set("someServicesDown", (numberOfServicesDown > 0 ? 1 : 0), context);

            boolean down = NodeHealthTracker.allDown(services);
            metric.set("nodeFailerBadNode", (down ? 1 : 0), context);

            boolean nodeDownInNodeRepo = node.history().event(History.Event.Type.down).isPresent();
            metric.set("downInNodeRepo", (nodeDownInNodeRepo ? 1 : 0), context);
        }

        metric.set("numberOfServices", numberOfServices, context);
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
        Map<State, List<Node>> nodesByState = nodes.nodeType(NodeType.tenant).asList().stream()
                                                   .collect(Collectors.groupingBy(Node::state));

        // Count per state
        for (State state : State.values()) {
            List<Node> nodesInState = nodesByState.getOrDefault(state, List.of());
            metric.set("hostedVespa." + state.name() + "Hosts", nodesInState.size(), null);
        }
    }

    private void updateLockMetrics() {
        LockStats.getGlobal().getLockMetricsByPath()
                .forEach((lockPath, lockMetrics) -> {
                    Metric.Context context = getContext(Map.of("lockPath", lockPath));

                    LatencyMetrics acquireLatencyMetrics = lockMetrics.getAndResetAcquireLatencyMetrics();
                    setNonZero("lockAttempt.acquireMaxActiveLatency", acquireLatencyMetrics.maxActiveLatencySeconds(), context);
                    setNonZero("lockAttempt.acquireHz", acquireLatencyMetrics.startHz(), context);
                    setNonZero("lockAttempt.acquireLoad", acquireLatencyMetrics.load(), context);

                    LatencyMetrics lockedLatencyMetrics = lockMetrics.getAndResetLockedLatencyMetrics();
                    setNonZero("lockAttempt.lockedLatency", lockedLatencyMetrics.maxLatencySeconds(), context);
                    setNonZero("lockAttempt.lockedLoad", lockedLatencyMetrics.load(), context);

                    setNonZero("lockAttempt.acquireTimedOut", lockMetrics.getAndResetAcquireTimedOutCount(), context);
                    setNonZero("lockAttempt.deadlock", lockMetrics.getAndResetDeadlockCount(), context);

                    // bucket for various rare errors - to reduce #metrics
                    setNonZero("lockAttempt.errors",
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
        metric.set("hostedVespa.docker.totalCapacityCpu", totalCapacity.vcpu(), null);
        metric.set("hostedVespa.docker.totalCapacityMem", totalCapacity.memoryGb(), null);
        metric.set("hostedVespa.docker.totalCapacityDisk", totalCapacity.diskGb(), null);

        NodeResources totalFreeCapacity = getFreeCapacityTotal(nodes);
        metric.set("hostedVespa.docker.freeCapacityCpu", totalFreeCapacity.vcpu(), null);
        metric.set("hostedVespa.docker.freeCapacityMem", totalFreeCapacity.memoryGb(), null);
        metric.set("hostedVespa.docker.freeCapacityDisk", totalFreeCapacity.diskGb(), null);
    }

    private void updateTenantUsageMetrics(NodeList nodes) {
        nodes.nodeType(NodeType.tenant).stream()
                .filter(node -> node.allocation().isPresent())
                .collect(Collectors.groupingBy(node -> node.allocation().get().owner()))
                .forEach(
                        (applicationId, applicationNodes) -> {
                            var allocatedCapacity = applicationNodes.stream()
                                    .map(node -> node.allocation().get().requestedResources().justNumbers())
                                    .reduce(new NodeResources(0, 0, 0, 0, any), NodeResources::add);

                            var context = getContext(dimensions(applicationId));

                            metric.set("hostedVespa.docker.allocatedCapacityCpu", allocatedCapacity.vcpu(), context);
                            metric.set("hostedVespa.docker.allocatedCapacityMem", allocatedCapacity.memoryGb(), context);
                            metric.set("hostedVespa.docker.allocatedCapacityDisk", allocatedCapacity.diskGb(), context);
                        }
                );
    }

    private void updateRepairTicketMetrics(NodeList nodes) {
        nodes.nodeType(NodeType.host).stream()
             .map(node -> node.reports().getReport("repairTicket"))
             .flatMap(Optional::stream)
             .map(report -> report.getInspector().field("status").asString())
             .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
             .forEach((status, number) -> metric.set("hostedVespa.breakfixedHosts", number, getContext(Map.of("status", status))));
    }

    static Map<String, String> dimensions(ApplicationId application, ClusterSpec.Id cluster) {
        Map<String, String> dimensions = new HashMap<>(dimensions(application));
        dimensions.put("clusterId", cluster.value());
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
                    .reduce(new NodeResources(0, 0, 0, 0, any), NodeResources::add);
    }

    private static NodeResources getFreeCapacityTotal(NodeList nodes) {
        return nodes.hosts().state(State.active).asList().stream()
                    .map(n -> freeCapacityOf(nodes, n))
                    .map(NodeResources::justNumbers)
                    .reduce(new NodeResources(0, 0, 0, 0, any), NodeResources::add);
    }

    private static NodeResources freeCapacityOf(NodeList nodes, Node host) {
        return nodes.childrenOf(host).asList().stream()
                    .map(node -> node.flavor().resources().justNumbers())
                    .reduce(host.flavor().resources().justNumbers(), NodeResources::subtract);
    }

    private static class ClusterKey {

        private final ApplicationId application;
        private final ClusterSpec.Id cluster;

        public ClusterKey(ApplicationId application, ClusterSpec.Id cluster) {
            this.application = application;
            this.cluster = cluster;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClusterKey that = (ClusterKey) o;
            return application.equals(that.application) &&
                   cluster.equals(that.cluster);
        }

        @Override
        public int hashCode() {
            return Objects.hash(application, cluster);
        }

    }

}
