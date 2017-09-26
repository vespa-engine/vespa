// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates cost for for an application instance.
 *
 * @author smorgrav
 */
public class CostApplication {

    private final Zone zone;
    private final ApplicationId appId;
    private final double utilization;
    private final double waste;
    private final double tco;

    private final Map<String, CostCluster> clusters;
    
    public CostApplication(Zone zone, ApplicationId appId, Map<String, CostCluster> clusterCosts) {
        this.zone = zone;
        this.appId = appId;
        clusters = new HashMap<>(clusterCosts);

        double tco = 0;
        double util = 0;
        double waste = 0;

        for (CostCluster costCluster : clusterCosts.values()) {
            tco += costCluster.getTco();
            waste += costCluster.getWaste();
            int nodesInCluster = costCluster.getClusterInfo().getHostnames().size();
            util = Math.max(util, nodesInCluster*costCluster.getResultUtilization().getMaxUtilization());
        }

        this.utilization = util;
        this.waste = waste;
        this.tco = tco;
    }
    
    public Zone getZone() {
        return zone;
    }

    public ApplicationId getAppId() {
        return appId;
    }

    public Map<String, CostCluster> getCluster() {
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
