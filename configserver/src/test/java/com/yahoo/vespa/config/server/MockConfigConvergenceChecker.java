// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.flags.InMemoryFlagSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class MockConfigConvergenceChecker extends ConfigConvergenceChecker {

    private final long wantedGeneration;

    public MockConfigConvergenceChecker(long wantedGeneration) {
        this(wantedGeneration, List.of());
    }

    public MockConfigConvergenceChecker(long wantedGeneration, List<ServiceInfo> servicesThatFailFirstIteration) {
        super();
        this.wantedGeneration = wantedGeneration;
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
}
