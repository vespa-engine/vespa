package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.restapi.cost.config.SelfHostedCostConfig;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.yolean.Exceptions.uncheck;

public class CostCalculator {

    private static final double SELF_HOSTED_DISCOUNT = .5;

    public static Map<Property, ResourceAllocation> calculateCost(NodeRepositoryClientInterface nodeRepository,
                                                                  Controller controller,
                                                                  Clock clock,
                                                                  SelfHostedCostConfig selfHostedCostConfig) {

        String date = LocalDate.now(clock).toString();

        List<NodeRepositoryNode> nodes = controller.zoneRegistry().zones()
                .reachable().in(Environment.prod).ofCloud(CloudName.from("yahoo")).ids().stream()
                .flatMap(zoneId -> uncheck(() -> nodeRepository.listNodes(zoneId, true).nodes().stream()))
                .filter(node -> node.getOwner() != null && !node.getOwner().getTenant().equals("hosted-vespa"))
                .collect(Collectors.toList());

        selfHostedCostConfig.properties().stream().map(property -> {
            NodeRepositoryNode selfHostedNode = new NodeRepositoryNode();

            NodeOwner owner = new NodeOwner();
            owner.tenant = property.name();
            selfHostedNode.setOwner(owner);
            selfHostedNode.setMinCpuCores(property.cpuCores() * SELF_HOSTED_DISCOUNT);
            selfHostedNode.setMinMainMemoryAvailableGb(property.memoryGb() * SELF_HOSTED_DISCOUNT);
            selfHostedNode.setMinDiskAvailableGb(property.diskGb() * SELF_HOSTED_DISCOUNT);

            return selfHostedNode;
        }).forEach(nodes::add);

        ResourceAllocation total = ResourceAllocation.from(date, nodes, null);

        Map<String, Property> propertyByTenantName = controller.tenants().asList().stream()
                .filter(AthenzTenant.class::isInstance)
                .collect(Collectors.toMap(
                        tenant -> tenant.name().value(),
                        tenant -> ((AthenzTenant) tenant).property()
                ));

        selfHostedCostConfig.properties().stream()
                .map(SelfHostedCostConfig.Properties::name)
                .forEach(name -> propertyByTenantName.put(name, new Property(name)));

        return nodes.stream()
                .filter(node -> propertyByTenantName.containsKey(node.getOwner().tenant))
                .collect(Collectors.groupingBy(
                        node -> propertyByTenantName.get(node.getOwner().tenant),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                (tenantNodes) -> ResourceAllocation.from(date, tenantNodes, total)
                        )
                ));
    }

    static class ResourceAllocation {
        final double cpuCores;
        final double memoryGb;
        final double diskGb;
        final String date;
        final ResourceAllocation total;

        private ResourceAllocation(String date, double cpuCores, double memoryGb, double diskGb, ResourceAllocation total) {
            this.date = date;
            this.cpuCores = cpuCores;
            this.memoryGb = memoryGb;
            this.diskGb = diskGb;
            this.total = total;
        }

        private static ResourceAllocation from(String date, List<NodeRepositoryNode> nodes, ResourceAllocation total) {
            return new ResourceAllocation(
                    date,
                    nodes.stream().mapToDouble(NodeRepositoryNode::getMinCpuCores).sum(),
                    nodes.stream().mapToDouble(NodeRepositoryNode::getMinMainMemoryAvailableGb).sum(),
                    nodes.stream().mapToDouble(NodeRepositoryNode::getMinDiskAvailableGb).sum(),
                    total
            );
        }

        private double usageFraction() {
            return (cpuCores / total.cpuCores + memoryGb / total.memoryGb + diskGb / total.diskGb) / 3;
        }
    }

    public static String toCsv(Map<Property, ResourceAllocation> resourceShareByProperty) {
        String header = "Date,Property,Reserved Cpu Cores,Reserved Memory GB,Reserved Disk Space GB,Usage Fraction\n";
        String entries = resourceShareByProperty.entrySet().stream()
                .sorted((Comparator.comparingDouble(entry -> entry.getValue().usageFraction())))
                .map(propertyEntry -> {
                    ResourceAllocation r = propertyEntry.getValue();
                    return Stream.of(r.date, propertyEntry.getKey(), r.cpuCores, r.memoryGb, r.diskGb, r.usageFraction())
                            .map(Object::toString).collect(Collectors.joining(","));
                })
                .collect(Collectors.joining("\n"));
        return header + entries;
    }

}
