// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.any;

/**
 * @author oyving
 */
public class MetricsReporter extends Maintainer {

    private final Metric metric;
    private final Function<HostName, Optional<HostInfo>> orchestrator;
    private final ServiceMonitor serviceMonitor;
    private final Map<Map<String, String>, Metric.Context> contextMap = new HashMap<>();
    private final Supplier<Integer> pendingRedeploymentsSupplier;
    private final Clock clock;

    MetricsReporter(NodeRepository nodeRepository,
                    Metric metric,
                    Orchestrator orchestrator,
                    ServiceMonitor serviceMonitor,
                    Supplier<Integer> pendingRedeploymentsSupplier,
                    Duration interval,
                    Clock clock) {
        super(nodeRepository, interval);
        this.metric = metric;
        this.orchestrator = orchestrator.getNodeStatuses();
        this.serviceMonitor = serviceMonitor;
        this.pendingRedeploymentsSupplier = pendingRedeploymentsSupplier;
        this.clock = clock;
    }

    @Override
    public void maintain() {
        NodeList nodes = nodeRepository().list();
        Map<HostName, List<ServiceInstance>> servicesByHost =
                serviceMonitor.getServiceModelSnapshot().getServiceInstancesByHostName();

        nodes.forEach(node -> updateNodeMetrics(node, servicesByHost));
        updateStateMetrics(nodes);
        updateMaintenanceMetrics();
        updateDockerMetrics(nodes);
        updateTenantUsageMetrics(nodes);
    }

    private void updateMaintenanceMetrics() {
        metric.set("hostedVespa.pendingRedeployments", pendingRedeploymentsSupplier.get(), null);
    }

    private void updateNodeMetrics(Node node, Map<HostName, List<ServiceInstance>> servicesByHost) {
        Metric.Context context;

        Optional<Allocation> allocation = node.allocation();
        if (allocation.isPresent()) {
            ApplicationId applicationId = allocation.get().owner();
            context = getContextAt(
                    "state", node.state().name(),
                    "host", node.hostname(),
                    "tenantName", applicationId.tenant().value(),
                    "applicationId", applicationId.serializedForm().replace(':', '.'),
                    "app", toApp(applicationId),
                    "clustertype", allocation.get().membership().cluster().type().name(),
                    "clusterid", allocation.get().membership().cluster().id().value());

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
            context = getContextAt(
                    "state", node.state().name(),
                    "host", node.hostname());
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

        orchestrator.apply(new HostName(node.hostname()))
                .ifPresent(info -> {
                    int suspended = info.status().isSuspended() ? 1 : 0;
                    metric.set("suspended", suspended, context);
                    metric.set("allowedToBeDown", suspended, context); // remove summer 2020.

                    info.suspendedSince().ifPresent(suspendedSince -> {
                        Duration duration = Duration.between(suspendedSince, clock.instant());
                        metric.set("suspendedSeconds", duration.getSeconds(), context);
                    });
                });

        long numberOfServices;
        HostName hostName = new HostName(node.hostname());
        List<ServiceInstance> services = servicesByHost.get(hostName);
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

            boolean badNode = NodeFailer.badNode(services);
            metric.set("nodeFailerBadNode", (badNode ? 1 : 0), context);

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

    private Metric.Context getContextAt(String... point) {
        if (point.length % 2 != 0)
            throw new IllegalArgumentException("Dimension specification comes in pairs");

        Map<String, String> dimensions = new HashMap<>();
        for (int i = 0; i < point.length; i += 2) {
            dimensions.put(point[i], point[i + 1]);
        }

        return contextMap.computeIfAbsent(dimensions, metric::createContext);
    }

    private void updateStateMetrics(NodeList nodes) {
        Map<Node.State, List<Node>> nodesByState = nodes.nodeType(NodeType.tenant).asList().stream()
                .collect(Collectors.groupingBy(Node::state));

        // Metrics pr state
        for (Node.State state : Node.State.values()) {
            List<Node> nodesInState = nodesByState.getOrDefault(state, List.of());
            metric.set("hostedVespa." + state.name() + "Hosts", nodesInState.size(), null);
        }
    }

    private void updateDockerMetrics(NodeList nodes) {
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

                            var context = getContextAt(
                                    "tenantName", applicationId.tenant().value(),
                                    "applicationId", applicationId.serializedForm().replace(':', '.'),
                                    "app", toApp(applicationId));

                            metric.set("hostedVespa.docker.allocatedCapacityCpu", allocatedCapacity.vcpu(), context);
                            metric.set("hostedVespa.docker.allocatedCapacityMem", allocatedCapacity.memoryGb(), context);
                            metric.set("hostedVespa.docker.allocatedCapacityDisk", allocatedCapacity.diskGb(), context);
                        }
                );
    }

    private static NodeResources getCapacityTotal(NodeList nodes) {
        return nodes.nodeType(NodeType.host).asList().stream()
                .map(host -> host.flavor().resources())
                .map(resources -> resources.justNumbers())
                .reduce(new NodeResources(0, 0, 0, 0, any), NodeResources::add);
    }

    private static NodeResources getFreeCapacityTotal(NodeList nodes) {
        return nodes.nodeType(NodeType.host).asList().stream()
                .map(n -> freeCapacityOf(nodes, n))
                .map(resources -> resources.justNumbers())
                .reduce(new NodeResources(0, 0, 0, 0, any), NodeResources::add);
    }

    private static NodeResources freeCapacityOf(NodeList nodes, Node dockerHost) {
        return nodes.childrenOf(dockerHost).asList().stream()
                .map(node -> node.flavor().resources().justNumbers())
                .reduce(dockerHost.flavor().resources().justNumbers(), NodeResources::subtract);
    }
}
