// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringClient;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Creates a {@link ResourceSnapshot} per application, which is then passed on to a MeteringClient
 *
 * @author olaa
 */
public class ResourceMeterMaintainer extends ControllerMaintainer {

    private final Clock clock;
    private final Metric metric;
    private final NodeRepository nodeRepository;
    private final MeteringClient meteringClient;
    private final CuratorDb curator;

    private static final String METERING_LAST_REPORTED = "metering_last_reported";
    private static final String METERING_TOTAL_REPORTED = "metering_total_reported";
    private static final int METERING_REFRESH_INTERVAL_SECONDS = 1800;

    @SuppressWarnings("WeakerAccess")
    public ResourceMeterMaintainer(Controller controller,
                                   Duration interval,
                                   Metric metric,
                                   MeteringClient meteringClient) {
        super(controller, interval, null, SystemName.allOf(SystemName::isPublic));
        this.clock = controller.clock();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.metric = metric;
        this.meteringClient = meteringClient;
        this.curator = controller.curator();
    }

    @Override
    protected boolean maintain() {
        try {
            collectResourceSnapshots();
            return true;
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to collect resource snapshots. Retrying in " + interval() + ". Error: " +
                                   Exceptions.toMessageString(e));
        }
        return false;
    }

    private void collectResourceSnapshots() {
        Collection<ResourceSnapshot> resourceSnapshots = getAllResourceSnapshots();
        meteringClient.consume(resourceSnapshots);

        metric.set(METERING_LAST_REPORTED, clock.millis() / 1000, metric.createContext(Collections.emptyMap()));
        // total metered resource usage, for alerting on drastic changes
        metric.set(METERING_TOTAL_REPORTED,
                   resourceSnapshots.stream()
                           .mapToDouble(r -> r.getCpuCores() + r.getMemoryGb() + r.getDiskGb()).sum(),
                   metric.createContext(Collections.emptyMap()));

        try (var lock = curator.lockMeteringRefreshTime()) {
            if (needsRefresh(curator.readMeteringRefreshTime())) {
                meteringClient.refresh();
                curator.writeMeteringRefreshTime(clock.millis());
            }
        } catch (TimeoutException ignored) {
            // If it's locked, it means we're currently refreshing
        }
    }

    private Collection<ResourceSnapshot> getAllResourceSnapshots() {
        return controller().zoneRegistry().zones()
                .ofCloud(CloudName.from("aws"))
                .reachable().zones().stream()
                .map(ZoneApi::getId)
                .map(zoneId -> createResourceSnapshotsFromNodes(zoneId, nodeRepository.list(zoneId)))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private Collection<ResourceSnapshot> createResourceSnapshotsFromNodes(ZoneId zoneId, List<Node> nodes) {
        return nodes.stream()
                .filter(this::unlessNodeOwnerIsHostedVespa)
                .filter(this::isNodeStateMeterable)
                .filter(this::isNodeTypeMeterable)
                .collect(Collectors.groupingBy(node ->
                                node.owner().get(),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        nodeList -> ResourceSnapshot.from(
                                                nodeList,
                                                clock.instant(),
                                                zoneId))
                                )).values();
    }

    private boolean unlessNodeOwnerIsHostedVespa(Node node) {
        return node.owner()
                .map(owner -> !owner.tenant().value().equals("hosted-vespa"))
                .orElse(false);
    }

    /**
     * Checks if the node is in some state where it is in active use by the tenant,
     * and not transitioning out of use, in a failed state, etc.
     */
    private static final Set<Node.State> METERABLE_NODE_STATES = Set.of(
            Node.State.reserved,   // an application will soon use this node
            Node.State.active,     // an application is currently using this node
            Node.State.inactive    // an application is not using it, but it is reserved for being re-introduced or decommissioned
    );

    private boolean isNodeStateMeterable(Node node) {
        return METERABLE_NODE_STATES.contains(node.state());
    }

    private boolean isNodeTypeMeterable(Node node) {
        return node.clusterType() != Node.ClusterType.admin; // log servers and shared cluster controllers
    }

    private boolean needsRefresh(long lastRefreshTimestamp) {
        return clock.instant()
                .minusSeconds(METERING_REFRESH_INTERVAL_SECONDS)
                .isAfter(Instant.ofEpochMilli(lastRefreshTimestamp));
    }

}
