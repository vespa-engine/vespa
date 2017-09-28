package com.yahoo.vespa.hosted.controller.maintenance;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetch utilization metrics and update applications with this data.
 *
 * @author smorgrav
 */
public class ClusterUtilizationMaintainer extends Maintainer {

    Controller controller;

    public ClusterUtilizationMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
        this.controller = controller;
    }

    private Map<ClusterSpec.Id, ClusterUtilization> getUpdatedClusterUtilizations(ApplicationId app, Zone zone) {
        List<MetricsService.ClusterCostMetrics> systemMetrics = controller.metricsService().getClusterCostMetrics(app, zone);

        Map<ClusterSpec.Id, ClusterUtilization> utilizationMap = new HashMap<>();
        for (MetricsService.ClusterCostMetrics clusterCostMetrics : systemMetrics) {
            MetricsService.CostMetrics systemMetric = clusterCostMetrics.costMetrics();
            ClusterUtilization utilization = new ClusterUtilization(systemMetric.memUtil() / 100, systemMetric.cpuUtil() / 100, systemMetric.diskUtil(), 0);
            utilizationMap.put(new ClusterSpec.Id(clusterCostMetrics.clusterId()), utilization);
        }

        return utilizationMap;
    }

    @Override
    protected void maintain() {
        for (Application application : controller().applications().asList()) {
            for (Deployment deployment : application.deployments().values()) {
                Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization = getUpdatedClusterUtilizations(application.id(), deployment.zone());
                application.with(deployment.withClusterUtils(clusterUtilization));
            }
        }
    }
}
