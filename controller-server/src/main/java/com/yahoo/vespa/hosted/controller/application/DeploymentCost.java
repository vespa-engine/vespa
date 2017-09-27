// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates cost for for an application instance.
 *
 * @author smorgrav
 */
public class DeploymentCost {

    private final double utilization;
    private final double waste;
    private final double tco;

    private final Map<String, ClusterCost> clusters;

    public DeploymentCost() {
        this(new HashMap<>());
    }

    public DeploymentCost(Map<String, ClusterCost> clusterCosts) {
        clusters = new HashMap<>(clusterCosts);

        double tco = 0;
        double util = 0;
        double waste = 0;

        for (ClusterCost costCluster : clusterCosts.values()) {
            tco += costCluster.getTco();
            waste += costCluster.getWaste();
            int nodesInCluster = costCluster.getClusterInfo().getHostnames().size();
            util = Math.max(util, nodesInCluster*costCluster.getResultUtilization().getMaxUtilization());
        }

        this.utilization = util;
        this.waste = waste;
        this.tco = tco;
    }

    public Map<String, ClusterCost> getCluster() {
        return clusters;
    }

    public double getTco() {
        return tco;
    }

    public double getUtilization() {
        return utilization;
    }

    public double getWaste() {
        return waste;
    }
}
