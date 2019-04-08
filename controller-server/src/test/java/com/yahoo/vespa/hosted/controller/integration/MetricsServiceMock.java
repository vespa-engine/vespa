// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class MetricsServiceMock extends AbstractComponent implements MetricsService {

    private final Map<String, Double> metrics = new HashMap<>();
    private final Map<String, Map<HostName, RotationStatus>> rotationStatus = new HashMap<>();

    public MetricsServiceMock addRotation(String rotationName) {
        rotationStatus.put(rotationName, new HashMap<>());
        return this;
    }

    public MetricsServiceMock setMetric(String key, Double value) {
        metrics.put(key, value);
        return this;
    }

    public MetricsServiceMock setZoneIn(String rotationName, String vipName) {
        if (!rotationStatus.containsKey(rotationName)) {
            throw new IllegalArgumentException("Unknown rotation: " + rotationName);
        }
        rotationStatus.get(rotationName).put(HostName.from(vipName), RotationStatus.IN);
        return this;
    }

    public MetricsServiceMock setZoneOut(String rotationName, String vipName) {
        if (!rotationStatus.containsKey(rotationName)) {
            throw new IllegalArgumentException("Unknown rotation: " + rotationName);
        }
        rotationStatus.get(rotationName).put(HostName.from(vipName), RotationStatus.OUT);
        return this;
    }

    @Override
    public ApplicationMetrics getApplicationMetrics(ApplicationId application) {
        return new ApplicationMetrics(metrics.getOrDefault("queryServiceQuality", 0.5),
                                      metrics.getOrDefault("writeServiceQuality", 0.7));
    }

    @Override
    public DeploymentMetrics getDeploymentMetrics(ApplicationId application, ZoneId zone) {
        return new DeploymentMetrics(metrics.getOrDefault("queriesPerSecond", 1D),
                                     metrics.getOrDefault("writesPerSecond", 2D),
                                     metrics.getOrDefault("docoumentCount", 3D).longValue(),
                                     metrics.getOrDefault("queryLatencyMillis", 4D),
                                     metrics.getOrDefault("writeLatencyMillis", 5D));
    }

    @Override
    public Map<String, SystemMetrics> getSystemMetrics(ApplicationId application, ZoneId zone) {
        Map<String, SystemMetrics> result = new HashMap<>();
        SystemMetrics system = new SystemMetrics(55.54, 69.90, 34.59);
        result.put("default", system);
        return result;
    }

    @Override
    public Map<HostName, RotationStatus> getRotationStatus(String rotationName) {
        return rotationStatus.get(rotationName);
    }

}
