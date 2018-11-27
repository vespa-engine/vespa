// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;

import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Periodically calculate and store cost allocation for properties.
 *
 * @author ldalves
 */
public class CostReportMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(CostReportMaintainer.class.getName());

    private final NodeRepositoryClientInterface nodeRepository;

    public CostReportMaintainer(Controller controller, Duration interval, JobControl jobControl, NodeRepositoryClientInterface nodeRepository) {
        super(controller, interval, jobControl, null, EnumSet.of(SystemName.main));
        this.nodeRepository = Objects.requireNonNull(nodeRepository, "node repository must be non-null");
    }

    @Override
    protected void maintain() {
        List<NodeRepositoryNode> nodes = controller().zoneRegistry().zones().reachable().ids().stream()
                .flatMap(zoneId -> uncheck(() -> nodeRepository.listNodes(zoneId,true).nodes().stream()))
                .filter(node -> node.getOwner() != null && !node.getOwner().getTenant().equals("hosted-vespa"))
                .collect(Collectors.toList());

        ResourceAllocation total = ResourceAllocation.from(nodes);

        Map<String, Property> propertyByTenantName = controller().tenants().asList().stream()
                .filter(AthenzTenant.class::isInstance)
                .collect(Collectors.toMap(
                        tenant -> tenant.name().value(),
                        tenant -> ((AthenzTenant) tenant).property()
                ));

        Map<Property, ResourceAllocation> resourceAllocationByProperty = nodes.stream()
                .filter(node -> propertyByTenantName.containsKey(node.getOwner().tenant))
                .collect(Collectors.groupingBy(
                        node -> propertyByTenantName.get(node.getOwner().tenant),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                ResourceAllocation::from
                        )
                ));

        Map<Property, Double> resourceShareByProperty = resourceAllocationByProperty.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().ratio(total)
                ));

        String csv = resourceShareByProperty.entrySet().stream()
                .sorted((Comparator.comparingDouble(Map.Entry::getValue)))
                .map(entry -> entry.getKey().id() + "," + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static class ResourceAllocation {
        final double cpuCores;
        final double memoryGb;
        final double diskGb;

        private ResourceAllocation(double cpuCores, double memoryGb, double diskGb) {
            this.cpuCores = cpuCores;
            this.memoryGb = memoryGb;
            this.diskGb = diskGb;
        }

        private static ResourceAllocation from(List<NodeRepositoryNode> nodes) {
            return new ResourceAllocation(
                    nodes.stream().mapToDouble(NodeRepositoryNode::getMinCpuCores).sum(),
                    nodes.stream().mapToDouble(NodeRepositoryNode::getMinMainMemoryAvailableGb).sum(),
                    nodes.stream().mapToDouble(NodeRepositoryNode::getMinDiskAvailableGb).sum()
            );
        }

        private double ratio(ResourceAllocation other) {
            return (cpuCores/other.cpuCores + memoryGb /other.memoryGb + diskGb /other.diskGb) / 3;
        }
    }

}
