// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.service.VespaServicesConfig;
import com.yahoo.vespa.model.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class VespaServicesConfigGenerator {

    public static List<VespaServicesConfig.Service.Builder> generate(List<Service> services) {
        return services.stream()
                .filter(VespaServicesConfigGenerator::doIncludeServiceMetrics)
                .map(VespaServicesConfigGenerator::toServiceBuilder)
                .collect(Collectors.toList());
    }

    private static boolean doIncludeServiceMetrics(Service s) {
        return s.getStartupCommand().isPresent() || s.getServiceType().equals("configserver") || s.getServiceType().equals("config-sentinel");
    }

    private static VespaServicesConfig.Service.Builder toServiceBuilder(Service service) {
        VespaServicesConfig.Service.Builder builder = new VespaServicesConfig.Service.Builder()
                .configId(service.getConfigId())
                .name(service.getServiceName())
                .port(service.getHealthPort());

        service.getDefaultMetricDimensions().forEach((name, value) -> builder.dimension(toServiceDimensionBuilder(name, value)));
        return builder;
    }

    private static VespaServicesConfig.Service.Dimension.Builder toServiceDimensionBuilder(String name, String value) {
        return new VespaServicesConfig.Service.Dimension.Builder()
                .key(name)
                .value(value);
    }

}
