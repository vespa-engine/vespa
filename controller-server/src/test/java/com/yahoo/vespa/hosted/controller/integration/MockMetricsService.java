// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;

import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class MockMetricsService implements com.yahoo.vespa.hosted.controller.api.integration.MetricsService {

    @Override
    public ApplicationMetrics getApplicationMetrics(ApplicationId application) {
        return new ApplicationMetrics(0.5, 0.7);
    }

    @Override
    public DeploymentMetrics getDeploymentMetrics(ApplicationId application, Zone zone) {
        return new DeploymentMetrics(1, 2, 3, 4, 5);
    }

    @Override
    public Map<String, SystemMetrics> getSystemMetrics(ApplicationId application, Zone zone) {
        Map<String, SystemMetrics> result = new HashMap<>();
        SystemMetrics system = new SystemMetrics(55.54, 69.90, 34.59);
        result.put("default", system);
        return result;
    }

}
