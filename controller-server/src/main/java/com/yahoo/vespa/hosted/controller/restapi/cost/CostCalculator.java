package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.zone.CloudName;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;

public class CostCalculator {
    public static Map<Property, Double> calculateCost(NodeRepositoryClientInterface nodeRepository, Controller controller) {
        List<NodeRepositoryNode> nodes = controller.zoneRegistry().zones()
                .reachable().in(Environment.prod).ofCloud(CloudName.from("yahoo")).ids().stream()
                .flatMap(zoneId -> uncheck(() -> nodeRepository.listNodes(zoneId, true).nodes().stream()))
                .filter(node -> node.getOwner() != null && !node.getOwner().getTenant().equals("hosted-vespa"))
                .collect(Collectors.toList());

        ResourceAllocation total = ResourceAllocation.from(nodes);

        Map<String, Property> propertyByTenantName = controller.tenants().asList().stream()
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

        return resourceAllocationByProperty.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().ratio(total)
                ));
    }

    static class ResourceAllocation {
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
            return (cpuCores / other.cpuCores + memoryGb / other.memoryGb + diskGb / other.diskGb) / 3;
        }
    }

    public static String toCsv(Map<Property, Double> resourceShareByProperty) {
        String header = "Property,Allocated fraction\n";
        String entries = resourceShareByProperty.entrySet().stream()
                .sorted((Comparator.comparingDouble(Map.Entry::getValue)))
                .map(entry -> entry.getKey().id() + "," + entry.getValue())
                .collect(Collectors.joining("\n"));
        return header + entries;
    }

}
