// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import java.util.HashMap;
import java.util.Map;

/**
 * Cost data model for an application instance. I.e one running vespa application in one zone.
 *
 * @author smorgrav
 */
// TODO: Make immutable
// TODO: Make the Application own this and rename to Cost
// TODO: Enforce constraints
// TODO: Remove application id elements
// TODO: Model zone as Zone
// TODO: Cost per zone + total
// TODO: Use doubles
public class ApplicationCost {

    /** This contains environment.region */
    private String zone;

    private String tenant;
    
    // This must contain applicationName.instanceName. TODO: Fix
    private String app;

    private int tco;
    private float utilization;
    private float waste;
    Map<String, ClusterCost> cluster;

    /** Create an empty (invalid) application cost */
    public ApplicationCost() {}
    
    public ApplicationCost(String zone, String tenant, String app, int tco, float utilization, float waste, 
                           Map<String, ClusterCost> clusterCost) {
        this.zone = zone;
        this.tenant = tenant;
        this.app = app;
        this.tco = tco;
        this.utilization = utilization;
        this.waste = waste;
        cluster = new HashMap<>(clusterCost);
    }
    
    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public Map<String, ClusterCost> getCluster() {
        return cluster;
    }

    public void setCluster(Map<String, ClusterCost> cluster) {
        this.cluster = cluster;
    }

    public int getTco() {
        return tco;
    }

    public void setTco(int tco) {
        if (tco < 0) throw new IllegalArgumentException("TCO cannot be negative");
        this.tco = tco;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public float getUtilization() {
        return utilization;
    }

    public void setUtilization(float utilization) {
        if (utilization < 0) throw new IllegalArgumentException("Utilization cannot be negative");
        this.utilization = utilization;
    }

    public float getWaste() {
        return waste;
    }

    public void setWaste(float waste) {
        this.waste = waste;
    }
}
