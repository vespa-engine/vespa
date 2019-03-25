// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshotConsumer;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 * Creates a ResourceSnapshot per application, which is then passed on to a ResourceSnapshotConsumer
 * Also write to file the raw data used to create the ResourceSnapshot.
 */
public class ResourceMeterMaintainer extends Maintainer {

    private final Clock clock;
    private final NodeRepositoryClientInterface nodeRepository;
    private final ResourceSnapshotConsumer resourceSnapshotConsumer;

    public ResourceMeterMaintainer(Controller controller,
                                   Duration interval,
                                   JobControl jobControl,
                                   NodeRepositoryClientInterface nodeRepository,
                                   Clock clock,
                                   ResourceSnapshotConsumer resourceSnapshotConsumer) {
        super(controller, interval, jobControl, ResourceMeterMaintainer.class.getSimpleName(), Set.of(SystemName.cd, SystemName.vaas));
        this.clock = clock;
        this.nodeRepository = nodeRepository;
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

        writeRawData(nodes, timeStamp);
        resourceSnapshotConsumer.consume(resourceSnapshots);
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
        return new ApplicationId(String.join(".", owner.getTenant(), owner.getApplication(), owner.getInstance()));
    }

    private void writeRawData(List<NodeRepositoryNode> nodes, Instant timeStamp) {
        try {
            new ObjectMapper().writeValue(new File(String.format("/path/to/file-%s", timeStamp.toString())), nodes);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write nodes to file", e);
        }

    }

}
