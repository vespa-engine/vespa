package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockConfigConvergenceChecker extends ConfigConvergenceChecker {

    private final long wantedGeneration;
    private final List<ServiceInfo> servicesThatFailFirstIteration;

    private int iteration = 0;

    public MockConfigConvergenceChecker(long wantedGeneration) {
        this(wantedGeneration, List.of());
    }

    public MockConfigConvergenceChecker(long wantedGeneration, List<ServiceInfo> servicesThatFailFirstIteration) {
        this.wantedGeneration = wantedGeneration;
        this.servicesThatFailFirstIteration = servicesThatFailFirstIteration;
    }

    @Override
    public Map<ServiceInfo, Long> getServiceConfigGenerations(Application application, Duration timeoutPerService) {
        return Map.of();
    }

    @Override
    public ServiceListResponse checkConvergenceForAllServices(Application application, Duration timeoutPerService) {
        return new ServiceListResponse(Map.of(), wantedGeneration, wantedGeneration);
    }

    @Override
    public ServiceResponse getServiceConfigGeneration(Application application, String hostAndPortToCheck, Duration timeout) {
        return new ServiceResponse(ServiceResponse.Status.ok, wantedGeneration);
    }

    @Override
    public ServiceListResponse checkConvergenceUnlessDeferringChangesUntilRestart(Application application, Set<String> hostnames) {
        iteration++;
        if (servicesThatFailFirstIteration.isEmpty() || iteration > 1)
            return new ServiceListResponse(Map.of(), wantedGeneration, wantedGeneration);

        Map<ServiceInfo, Long> services = new HashMap<>();
        for (var service : servicesThatFailFirstIteration) {
            services.put(service, wantedGeneration - 1);
        }
        return new ServiceListResponse(services, wantedGeneration, wantedGeneration - 1);
    }

}
