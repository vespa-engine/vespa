package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeOwner;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.restapi.cost.config.SelfHostedCostConfig;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.yolean.Exceptions.uncheck;

public class CostCalculator {

    private static final double SELF_HOSTED_DISCOUNT = .5;

    public static String resourceShareByPropertyToCsv(NodeRepositoryClientInterface nodeRepository,
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

        ResourceAllocation totalResourceAllocation = ResourceAllocation.from(nodes);

        Map<String, Property> propertyByTenantName = controller.tenants().asList().stream()
                .filter(AthenzTenant.class::isInstance)
                .collect(Collectors.toMap(
                        tenant -> tenant.name().value(),
                        tenant -> ((AthenzTenant) tenant).property()
                ));

        selfHostedCostConfig.properties().stream()
                .map(SelfHostedCostConfig.Properties::name)
                .forEach(name -> propertyByTenantName.put(name, new Property(name)));

        Map<Property, ResourceAllocation> resourceShareByProperty = nodes.stream()
                .filter(node -> propertyByTenantName.containsKey(node.getOwner().tenant))
                .collect(Collectors.groupingBy(
                        node -> propertyByTenantName.get(node.getOwner().tenant),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                (tenantNodes) -> ResourceAllocation.from(tenantNodes)
                        )
                ));

        return toCsv(resourceShareByProperty, date, totalResourceAllocation);
    }

    private static String toCsv(Map<Property, ResourceAllocation> resourceShareByProperty, String date, ResourceAllocation totalResourceAllocation) {
        String header = "Date,Property,Reserved Cpu Cores,Reserved Memory GB,Reserved Disk Space GB,Usage Fraction\n";
        String entries = resourceShareByProperty.entrySet().stream()
                .sorted((Comparator.comparingDouble(entry -> entry.getValue().usageFraction(totalResourceAllocation))))
                .map(propertyEntry -> {
                    ResourceAllocation r = propertyEntry.getValue();
                    return Stream.of(date, propertyEntry.getKey(), r.getCpuCores(), r.getMemoryGb(), r.getDiskGb(), r.usageFraction(totalResourceAllocation))
                            .map(Object::toString).collect(Collectors.joining(","));
                })
                .collect(Collectors.joining("\n"));
        return header + entries;
    }
}
