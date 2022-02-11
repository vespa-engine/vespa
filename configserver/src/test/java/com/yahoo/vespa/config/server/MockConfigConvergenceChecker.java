package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;

import java.time.Duration;
import java.util.Map;

public class MockConfigConvergenceChecker extends ConfigConvergenceChecker {

    private final long wantedGeneration;

    public MockConfigConvergenceChecker(long wantedGeneration) {
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

    @Override
    public ServiceListResponse checkConvergenceUnlessDeferringChangesUntilRestart(Application application) {
        return new ServiceListResponse(Map.of(), wantedGeneration, wantedGeneration);
    }

}
