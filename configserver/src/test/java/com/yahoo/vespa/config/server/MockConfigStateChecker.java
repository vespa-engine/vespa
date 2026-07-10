// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.ConfigStateChecker;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MockConfigStateChecker extends ConfigStateChecker {

    private final long wantedGeneration;
    private final List<ServiceInfo> servicesThatFailFirstIteration;

    private int iteration = 0;

    public MockConfigStateChecker(long wantedGeneration) {
        this(wantedGeneration, List.of());
    }

    public MockConfigStateChecker(long wantedGeneration, List<ServiceInfo> servicesThatFailFirstIteration) {
        this.wantedGeneration = wantedGeneration;
        this.servicesThatFailFirstIteration = servicesThatFailFirstIteration;
    }

    @Override
    public Map<ServiceInfo, ServiceConfigState> getServiceConfigStates(List<ServiceInfo> services, Duration timeout) {
        iteration++;
        boolean stillFailing = !servicesThatFailFirstIteration.isEmpty() && iteration <= 1;
        long generation = stillFailing ? wantedGeneration - 1 : wantedGeneration;

        Map<ServiceInfo, ServiceConfigState> result = new LinkedHashMap<>();
        for (ServiceInfo service : services) {
            result.put(service, new ServiceConfigState(service.getServiceName(), generation, Optional.of(false)));
        }
        return result;
    }

}
