// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import com.yahoo.config.provision.Zone;

import java.util.HashMap;
import java.util.Map;

/**
 * Cost data model for an application instance. I.e one running vespa application in one zone.
 *
 * @author smorgrav
 */
// TODO: Make the Application own this and rename to Cost
// TODO: Enforce constraints
// TODO: Remove application id elements
public class CostApplication {

    /** This contains environment.region */
    private final Zone zone;

    private final String tenant;
    
    // This must contain applicationName.instanceName. TODO: Fix
    private final String app;

    private final int tco;
    private final double utilization;
    private final double waste;
    private final Map<String, CostCluster> cluster;

    
    public CostApplication(Zone zone, String tenant, String app, int tco, float utilization, float waste,
                           Map<String, CostCluster> clusterCost) {
        if (utilization < 0) throw new IllegalArgumentException("Utilization cannot be negative");
        this.zone = zone;
        this.tenant = tenant;
        this.app = app;
        this.tco = tco;
        this.utilization = utilization;
        this.waste = waste;
        cluster = new HashMap<>(clusterCost);
    }
    
    public Zone getZone() {
        return zone;
    }

    public String getApp() {
        return app;
    }

    public Map<String, CostCluster> getCluster() {
        return cluster;
    }

    public int getTco() {
        return tco;
    }

    public String getTenant() {
        return tenant;
    }

    public double getUtilization() {
        return utilization;
    }

    public double getWaste() {
        return waste;
    }
}
