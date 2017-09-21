// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;

import java.util.Collections;
import java.util.List;

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
    public List<ClusterCostMetrics> getClusterCostMetrics(ApplicationId application, Zone zone) {
        CostMetrics costMetrics = new CostMetrics(55.54, 69.90, 34.59);
        ClusterCostMetrics clusterCostMetrics = new ClusterCostMetrics("default", costMetrics);
        return Collections.singletonList(clusterCostMetrics);
    }

}
