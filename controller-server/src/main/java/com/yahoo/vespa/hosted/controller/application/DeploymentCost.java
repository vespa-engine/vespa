// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates cost for for an application deployment.
 *
 * @author smorgrav
 */
public class DeploymentCost {

    private final double utilization;
    private final double waste;
    private final double tco;

    private final Map<String, ClusterCost> clusters;

    DeploymentCost(Map<String, ClusterCost> clusterCosts) {
        clusters = new HashMap<>(clusterCosts);

        double tco = 0;
        double util = 0;
        double waste = 0;
        double maxWaste = -1;

        for (ClusterCost costCluster : clusterCosts.values()) {
            tco += costCluster.getTco();
            waste += costCluster.getWaste();

            if (costCluster.getWaste() > maxWaste) {
                util = costCluster.getResultUtilization().getMaxUtilization();
                maxWaste = costCluster.getWaste();
            }
        }

        this.utilization = util;
        this.waste = waste;
        this.tco = tco;
    }

    public Map<String, ClusterCost> getCluster() {
        return clusters;
    }

    /** @return Total cost of ownership for the deployment (sum of all clusters) */
    public double getTco() {
        return tco;
    }

    /** @return The utilization of clusters that wastes most money in this deployment */
    public double getUtilization() {
        return utilization;
    }

    /** @return The amount of dollars spent and not utilized */
    public double getWaste() {
        return waste;
    }
}
