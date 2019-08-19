// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates a ResourceSnapshot per application, which is then passed on to a MeteringClient
 *
 * @author olaa
 */
public class ResourceMeterMaintainer extends Maintainer {

    private final Clock clock;
    private final Metric metric;
    private final NodeRepository nodeRepository;
    private final MeteringClient meteringClient;

    private static final String METERING_LAST_REPORTED = "metering_last_reported";
    private static final String METERING_TOTAL_REPORTED = "metering_total_reported";

    @SuppressWarnings("WeakerAccess")
    public ResourceMeterMaintainer(Controller controller,
                                   Duration interval,
                                   JobControl jobControl,
                                   NodeRepository nodeRepository,
                                   Clock clock,
                                   Metric metric,
                                   MeteringClient meteringClient) {
        super(controller, interval, jobControl, null, SystemName.all());
        this.clock = clock;
        this.nodeRepository = nodeRepository;
        this.metric = metric;
        this.meteringClient = meteringClient;
    }

    @Override
    protected void maintain() {
        List<NodeRepositoryNode> nodes = getNodes();
        List<ResourceSnapshot> resourceSnapshots = getResourceSnapshots(nodes);

        meteringClient.consume(resourceSnapshots);

        metric.set(METERING_LAST_REPORTED, clock.millis() / 1000, metric.createContext(Collections.emptyMap()));
        metric.set(METERING_TOTAL_REPORTED, resourceSnapshots.stream()
                        .mapToDouble(r -> r.getCpuCores() + r.getMemoryGb() + r.getDiskGb()) // total metered resource usage, for alerting on drastic changes
                        .sum()
                , metric.createContext(Collections.emptyMap()));
    }

    private List<NodeRepositoryNode> getNodes() {
        return controller().zoneRegistry().zones()
                .ofCloud(CloudName.from("aws"))
                .reachable().zones().stream()
                .flatMap(zone -> nodeRepository.listNodes(zone.getId()).nodes().stream())
                .filter(node -> node.getOwner() != null && !node.getOwner().getTenant().equals("hosted-vespa"))
                .filter(node -> node.getState() == NodeState.active)
                .collect(Collectors.toList());
    }

    private List<ResourceSnapshot> getResourceSnapshots(List<NodeRepositoryNode> nodes) {
        return nodes.stream()
                .collect(Collectors.groupingBy(
                        node -> applicationIdFromNodeOwner(node.getOwner()),
                        Collectors.collectingAndThen(Collectors.toList(), nodeList -> ResourceSnapshot.from(nodeList, clock.instant()))
                )).values().stream().collect(Collectors.toList());
    }

    private ApplicationId applicationIdFromNodeOwner(NodeOwner owner) {
        return ApplicationId.from(owner.getTenant(), owner.getApplication(), owner.getInstance());
    }

}
