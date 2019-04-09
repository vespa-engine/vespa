// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetch utilization metrics and update applications with this data.
 *
 * @author smorgrav
 */
public class ClusterUtilizationMaintainer extends Maintainer {

    private final Controller controller;

    public ClusterUtilizationMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
        this.controller = controller;
    }

    private Map<ClusterSpec.Id, ClusterUtilization> getUpdatedClusterUtilizations(ApplicationId app, ZoneId zone) {
        Map<String, MetricsService.SystemMetrics> systemMetrics = controller.metricsService().getSystemMetrics(app, zone);

        Map<ClusterSpec.Id, ClusterUtilization> utilizationMap = new HashMap<>();
        for (Map.Entry<String, MetricsService.SystemMetrics> metrics : systemMetrics.entrySet()) {
            MetricsService.SystemMetrics systemMetric = metrics.getValue();
            ClusterUtilization utilization = new ClusterUtilization(systemMetric.memUtil() / 100, systemMetric.cpuUtil() / 100, systemMetric.diskUtil() / 100, 0);
            utilizationMap.put(new ClusterSpec.Id(metrics.getKey()), utilization);
        }

        return utilizationMap;
    }

    @Override
    protected void maintain() {
        for (Application application : controller().applications().asList()) {
            for (Deployment deployment : application.deployments().values()) {

                Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization = getUpdatedClusterUtilizations(application.id(), deployment.zone());

                controller().applications().lockIfPresent(application.id(), lockedApplication ->
                    controller().applications().store(lockedApplication.withClusterUtilization(deployment.zone(), clusterUtilization)));
            }
        }
    }

}
