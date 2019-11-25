package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author ldalves
 */
public class CostCalculator {

    private static final double SELF_HOSTED_DISCOUNT = .5;

    public static String resourceShareByPropertyToCsv(NodeRepository nodeRepository,
                                                      Controller controller,
                                                      Clock clock,
                                                      Map<Property, ResourceAllocation> fixedAllocations,
                                                      CloudName cloudName) {

        var date = new SimpleDateFormat("yyyy-MM-dd").format(Date.from(clock.instant()));

        // Group properties by tenant name
        Map<TenantName, Property> propertyByTenantName = controller.tenants().asList().stream()
                                                                   .filter(AthenzTenant.class::isInstance)
                                                                   .collect(Collectors.toMap(Tenant::name,
                                                                                             tenant -> ((AthenzTenant) tenant).property()));

        // Sum up allocations
        Map<Property, ResourceAllocation> allocationByProperty = new HashMap<>();
        var nodes = controller.zoneRegistry().zones()
                              .reachable().in(Environment.prod).ofCloud(cloudName).zones().stream()
                              .flatMap(zone -> uncheck(() -> nodeRepository.list(zone.getId()).stream()))
                              .filter(node -> node.owner().isPresent() && !node.owner().get().tenant().value().equals("hosted-vespa"))
                              .collect(Collectors.toList());
        var totalAllocation = ResourceAllocation.ZERO;
        for (var node : nodes) {
            Property property = propertyByTenantName.get(node.owner().get().tenant());
            if (property == null) continue;
            var allocation = allocationByProperty.getOrDefault(property, ResourceAllocation.ZERO);
            var nodeAllocation = new ResourceAllocation(node.resources().vcpu(), node.resources().memoryGb(), node.resources().diskGb());
            allocationByProperty.put(property, allocation.plus(nodeAllocation));
            totalAllocation = totalAllocation.plus(nodeAllocation);
        }

        // Add fixed allocations from config
        if (cloudName.equals(CloudName.from("yahoo"))) {
            for (var kv : fixedAllocations.entrySet()) {
                var property = kv.getKey();
                var allocation = allocationByProperty.getOrDefault(property, ResourceAllocation.ZERO);
                var discountedFixedAllocation = kv.getValue().multiply(SELF_HOSTED_DISCOUNT);
                allocationByProperty.put(property, allocation.plus(discountedFixedAllocation));
                totalAllocation = totalAllocation.plus(discountedFixedAllocation);
            }
        }

        return toCsv(allocationByProperty, date, totalAllocation);
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
