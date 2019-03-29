// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshotConsumer;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Creates a ResourceSnapshot per application, which is then passed on to a ResourceSnapshotConsumer
 * TODO: Write JSON blob of node repo somewhere
 *
 * @author olaa
 */
public class ResourceMeterMaintainer extends Maintainer {

    private final Clock clock;
    private final Metric metric;
    private final NodeRepositoryClientInterface nodeRepository;
    private final ResourceSnapshotConsumer resourceSnapshotConsumer;

    private static final String metering_last_reported = "metering_last_reported";
    private static final String metering_total_reported = "metering_total_reported";

    @SuppressWarnings("WeakerAccess")
    public ResourceMeterMaintainer(Controller controller,
                                   Duration interval,
                                   JobControl jobControl,
                                   NodeRepositoryClientInterface nodeRepository,
                                   Clock clock,
                                   Metric metric,
                                   ResourceSnapshotConsumer resourceSnapshotConsumer) {
        super(controller, interval, jobControl, ResourceMeterMaintainer.class.getSimpleName(), Set.of(SystemName.cd));
        this.clock = clock;
        this.nodeRepository = nodeRepository;
        this.metric = metric;
        this.resourceSnapshotConsumer = resourceSnapshotConsumer;
    }

    @Override
    protected void maintain() {
        List<NodeRepositoryNode> nodes = getNodes();
        Map<ApplicationId, ResourceAllocation> resourceAllocationByApplication = getResourceAllocationByApplication(nodes);

        // For now, we're only interested in resource allocation
        Instant timeStamp = clock.instant();
        Map<ApplicationId, ResourceSnapshot> resourceSnapshots = resourceAllocationByApplication.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> new ResourceSnapshot(e.getValue(), timeStamp))
                );


        resourceSnapshotConsumer.consume(resourceSnapshots);

        metric.set(metering_last_reported, clock.millis() / 1000, metric.createContext(Collections.emptyMap()));
        metric.set(metering_total_reported, resourceSnapshots.values().stream()
                        .map(ResourceSnapshot::getResourceAllocation)
                        .mapToDouble(r -> r.getCpuCores() + r.getMemoryGb() + r.getDiskGb()) // total metered resource usage, for alerting on drastic changes
                        .sum()
                , metric.createContext(Collections.emptyMap()));
    }

    private List<NodeRepositoryNode> getNodes() {
        return controller().zoneRegistry().zones()
                .reachable().ids().stream()
                .flatMap(zoneId -> uncheck(() -> nodeRepository.listNodes(zoneId, true).nodes().stream()))
                .filter(node -> node.getOwner() != null && !node.getOwner().getTenant().equals("hosted-vespa"))
                .collect(Collectors.toList());
    }

    private Map<ApplicationId, ResourceAllocation> getResourceAllocationByApplication(List<NodeRepositoryNode> nodes) {
        Map<ApplicationId, List<NodeRepositoryNode>> applicationNodes = new HashMap<>();

        nodes.stream().forEach(node -> applicationNodes.computeIfAbsent(applicationIdFromNodeOwner(node.getOwner()), n -> new ArrayList<>()).add(node));

        return applicationNodes.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                entry -> entry.getKey(),
                                entry -> ResourceAllocation.from(entry.getValue())
                        )
                );
    }

    private ApplicationId applicationIdFromNodeOwner(NodeOwner owner) {
        return ApplicationId.from(owner.getTenant(), owner.getApplication(), owner.getInstance());
    }

}
