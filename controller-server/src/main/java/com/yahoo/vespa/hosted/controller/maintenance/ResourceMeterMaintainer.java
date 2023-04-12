// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates a {@link ResourceSnapshot} per application, which is then passed on to a MeteringClient
 *
 * @author olaa
 */
public class ResourceMeterMaintainer extends ControllerMaintainer {

    /**
     * Checks if the node is in some state where it is in active use by the tenant,
     * and not transitioning out of use, in a failed state, etc.
     */
    private static final Set<Node.State> METERABLE_NODE_STATES = EnumSet.of(
            Node.State.reserved,   // an application will soon use this node
            Node.State.active,     // an application is currently using this node
            Node.State.inactive    // an application is not using it, but it is reserved for being re-introduced or decommissioned
    );

    private final ApplicationController applications;
    private final NodeRepository nodeRepository;
    private final ResourceDatabaseClient resourceClient;
    private final CuratorDb curator;
    private final SystemName systemName;
    private final Metric metric;
    private final Clock clock;

    private static final String METERING_LAST_REPORTED = "metering_last_reported";
    private static final String METERING_TOTAL_REPORTED = "metering_total_reported";
    private static final int METERING_REFRESH_INTERVAL_SECONDS = 1800;

    @SuppressWarnings("WeakerAccess")
    public ResourceMeterMaintainer(Controller controller,
                                   Duration interval,
                                   Metric metric,
                                   ResourceDatabaseClient resourceClient) {
        super(controller, interval);
        this.applications = controller.applications();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.resourceClient = resourceClient;
        this.curator = controller.curator();
        this.systemName = controller.serviceRegistry().zoneRegistry().system();
        this.metric = metric;
        this.clock = controller.clock();
    }

    @Override
    protected double maintain() {
        Collection<ResourceSnapshot> resourceSnapshots;
        try {
            resourceSnapshots = getAllResourceSnapshots();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to collect resource snapshots. Retrying in " + interval() + ". Error: " +
                                   Exceptions.toMessageString(e));
            return 1.0;
        }

        if (systemName.isPublic()) reportResourceSnapshots(resourceSnapshots);
        if (systemName.isPublic()) reportAllScalingEvents();
        updateDeploymentCost(resourceSnapshots);
        return 0.0;
    }

    void updateDeploymentCost(Collection<ResourceSnapshot> resourceSnapshots) {
        resourceSnapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> TenantAndApplicationId.from(snapshot.getApplicationId()),
                         Collectors.groupingBy(snapshot -> snapshot.getApplicationId().instance())))
                .forEach(this::updateDeploymentCost);
    }

    private void updateDeploymentCost(TenantAndApplicationId tenantAndApplication, Map<InstanceName, List<ResourceSnapshot>> snapshotsByInstance) {
        try {
            applications.lockApplicationIfPresent(tenantAndApplication, locked -> {
                for (InstanceName instanceName : locked.get().instances().keySet()) {
                    Map<ZoneId, Double> deploymentCosts = snapshotsByInstance.getOrDefault(instanceName, List.of()).stream()
                            .collect(Collectors.toUnmodifiableMap(
                                    ResourceSnapshot::getZoneId,
                                    snapshot -> cost(snapshot.resources(), systemName),
                                    Double::sum));
                    locked = locked.with(instanceName, i -> i.withDeploymentCosts(deploymentCosts));
                    updateCostMetrics(tenantAndApplication.instance(instanceName), deploymentCosts);
                }
                applications.store(locked);
            });
        } catch (UncheckedTimeoutException ignored) {
            // Will be retried on next maintenance, avoid throwing so we can update the other apps instead
        }
    }

    private void reportResourceSnapshots(Collection<ResourceSnapshot> resourceSnapshots) {
        resourceClient.writeResourceSnapshots(resourceSnapshots);

        updateMeteringMetrics(resourceSnapshots);

        try (var lock = curator.lockMeteringRefreshTime()) {
            if (needsRefresh(curator.readMeteringRefreshTime())) {
                resourceClient.refreshMaterializedView();
                curator.writeMeteringRefreshTime(clock.millis());
            }
        } catch (TimeoutException ignored) {
            // If it's locked, it means we're currently refreshing
        }
    }

    private List<ResourceSnapshot> getAllResourceSnapshots() {
        return controller().zoneRegistry().zones()
                .reachable().zones().stream()
                .map(ZoneApi::getId)
                .map(zoneId -> createResourceSnapshotsFromNodes(zoneId, nodeRepository.list(zoneId, NodeFilter.all())))
                .flatMap(Collection::stream)
                .toList();
    }

    private Stream<Instance> mapApplicationToInstances(Application application) {
        return application.instances().values().stream();
    }

    private Stream<DeploymentId> mapInstanceToDeployments(Instance instance) {
        return instance.deployments().keySet().stream()
                .filter(zoneId -> !zoneId.environment().isTest())
                .map(zoneId -> new DeploymentId(instance.id(), zoneId));
    }

    private Stream<Map.Entry<ClusterId, List<Cluster.ScalingEvent>>> mapDeploymentToClusterScalingEvent(DeploymentId deploymentId) {
        try {
            // TODO: get Application from controller.applications().deploymentInfo()
            return nodeRepository.getApplication(deploymentId.zoneId(), deploymentId.applicationId())
                    .clusters().entrySet().stream()
                    .map(cluster -> Map.entry(new ClusterId(deploymentId, cluster.getKey()), cluster.getValue().scalingEvents()));
        } catch (ConfigServerException e) {
            log.info("Could not retrieve scaling events for " + deploymentId + ": " + e.getMessage());
            return Stream.empty();
        }
    }

    private void reportAllScalingEvents() {
        var clusters = controller().applications().asList().stream()
                .flatMap(this::mapApplicationToInstances)
                .flatMap(this::mapInstanceToDeployments)
                .flatMap(this::mapDeploymentToClusterScalingEvent)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));

        for (var cluster : clusters.entrySet()) {
            resourceClient.writeScalingEvents(cluster.getKey(), cluster.getValue());
        }
    }

    private Collection<ResourceSnapshot> createResourceSnapshotsFromNodes(ZoneId zoneId, List<Node> nodes) {
        return nodes.stream()
                .filter(this::unlessNodeOwnerIsSystemApplication)
                .filter(this::isNodeStateMeterable)
                .filter(this::isClusterTypeMeterable)
                // Grouping by ApplicationId -> Architecture -> ResourceSnapshot
                .collect(Collectors.groupingBy(node ->
                        node.owner().get(),
                        groupSnapshotsByArchitectureAndMajorVersion(zoneId)))
                .values()
                .stream()
                .flatMap(byArch -> byArch.values().stream())
                .flatMap(byMajor -> byMajor.values().stream())
                .toList();
    }

    private boolean unlessNodeOwnerIsSystemApplication(Node node) {
        return node.owner()
                   .map(owner -> !owner.tenant().equals(SystemApplication.TENANT))
                   .orElse(false);
    }

    private boolean isNodeStateMeterable(Node node) {
        return METERABLE_NODE_STATES.contains(node.state());
    }

    private boolean isClusterTypeMeterable(Node node) {
        return node.clusterType() != Node.ClusterType.admin; // log servers and shared cluster controllers
    }

    private boolean needsRefresh(long lastRefreshTimestamp) {
        return clock.instant()
                .minusSeconds(METERING_REFRESH_INTERVAL_SECONDS)
                .isAfter(Instant.ofEpochMilli(lastRefreshTimestamp));
    }

    public static double cost(ClusterResources clusterResources, SystemName systemName) {
        NodeResources nr = clusterResources.nodeResources();
        return cost(new ResourceAllocation(nr.vcpu(), nr.memoryGb(), nr.diskGb(), nr.architecture()).multiply(clusterResources.nodes()), systemName);
    }

    private static double cost(ResourceAllocation allocation, SystemName systemName) {
        var resources = new NodeResources(allocation.getCpuCores(), allocation.getMemoryGb(), allocation.getDiskGb(), 0);
        return cost(resources, systemName);
    }

    private static double cost(NodeResources resources, SystemName systemName) {
        // Divide cost by 3 in non-public zones to show approx. AWS equivalent cost
        double costDivisor = systemName.isPublic() ? 1.0 : 3.0;
        return Math.round(resources.cost() * 100.0 / costDivisor) / 100.0;
    }

    private void updateMeteringMetrics(Collection<ResourceSnapshot> resourceSnapshots) {
        metric.set(METERING_LAST_REPORTED, clock.millis() / 1000, metric.createContext(Collections.emptyMap()));
        // total metered resource usage, for alerting on drastic changes
        metric.set(METERING_TOTAL_REPORTED,
                resourceSnapshots.stream()
                        .mapToDouble(r -> r.resources().vcpu() + r.resources().memoryGb() + r.resources().diskGb()).sum(),
                metric.createContext(Collections.emptyMap()));

        resourceSnapshots.forEach(snapshot -> {
            var context = getMetricContext(snapshot);
            metric.set("metering.vcpu", snapshot.resources().vcpu(), context);
            metric.set("metering.memoryGB", snapshot.resources().memoryGb(), context);
            metric.set("metering.diskGB", snapshot.resources().diskGb(), context);
        });
    }

    private void updateCostMetrics(ApplicationId applicationId, Map<ZoneId, Double> deploymentCost) {
        deploymentCost.forEach((zoneId, cost) -> {
            var context = getMetricContext(applicationId, zoneId);
            metric.set("metering.cost.hourly", cost, context);
        });
    }

    private Metric.Context getMetricContext(ApplicationId applicationId, ZoneId zoneId) {
        return metric.createContext(Map.of(
                "tenant", applicationId.tenant().value(),
                "applicationId", applicationId.toFullString(),
                "zoneId", zoneId.value()
        ));
    }

    private Metric.Context getMetricContext(ResourceSnapshot snapshot) {
        return metric.createContext(Map.of(
                "tenant", snapshot.getApplicationId().tenant().value(),
                "applicationId", snapshot.getApplicationId().toFullString(),
                "zoneId", snapshot.getZoneId().value(),
                "architecture", snapshot.resources().architecture()
        ));
    }

    private Collector<Node, ?, Map<NodeResources.Architecture, Map<Integer, ResourceSnapshot>>> groupSnapshotsByArchitectureAndMajorVersion(ZoneId zoneId) {
        return Collectors.groupingBy(
                (Node node) -> node.resources().architecture(),
                Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                (Node node) -> node.wantedVersion().getMajor(),
                                Collectors.toList()),
                        convertNodeListToResourceSnapshot(zoneId)));
    }

    private Function<Map<Integer, List<Node>>, Map<Integer, ResourceSnapshot>> convertNodeListToResourceSnapshot(ZoneId zoneId) {
        return nodesByMajor -> {
            return nodesByMajor.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey(),
                            entry -> ResourceSnapshot.from(entry.getValue(), clock.instant(), zoneId)));
        };
    }
}
