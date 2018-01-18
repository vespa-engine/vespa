// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec.Id;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A deployment of an application in a particular zone.
 * 
 * @author bratseth
 * @author smorgrav
 */
public class Deployment {

    private final ZoneId zone;
    private final ApplicationVersion applicationVersion;
    private final Version version;
    private final Instant deployTime;
    private final Map<Id, ClusterUtilization> clusterUtils;
    private final Map<Id, ClusterInfo> clusterInfo;
    private final DeploymentMetrics metrics;

    public Deployment(ZoneId zone, ApplicationVersion applicationVersion, Version version, Instant deployTime) {
        this(zone, applicationVersion, version, deployTime, new HashMap<>(), new HashMap<>(), new DeploymentMetrics());
    }

    public Deployment(ZoneId zone, ApplicationVersion applicationVersion, Version version, Instant deployTime,
                      Map<Id, ClusterUtilization> clusterUtils, Map<Id, ClusterInfo> clusterInfo, DeploymentMetrics metrics) {
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(applicationVersion, "applicationVersion cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(deployTime, "deployTime cannot be null");
        Objects.requireNonNull(clusterUtils, "clusterUtils cannot be null");
        Objects.requireNonNull(clusterInfo, "clusterInfo cannot be null");
        Objects.requireNonNull(metrics, "deployment metrics cannot be null");
        this.zone = zone;
        this.applicationVersion = applicationVersion;
        this.version = version;
        this.deployTime = deployTime;
        this.clusterUtils = clusterUtils;
        this.clusterInfo = clusterInfo;
        this.metrics = metrics;
    }

    /** Returns the zone this was deployed to */
    public ZoneId zone() { return zone; }

    /** Returns the deployed application version */
    public ApplicationVersion applicationVersion() { return applicationVersion; }
    
    /** Returns the deployed Vespa version */
    public Version version() { return version; }

    /** Returns the time this was deployed */
    public Instant at() { return deployTime; }

    public  Map<Id, ClusterInfo> clusterInfo() {
        return clusterInfo;
    }

    public  Map<Id, ClusterUtilization> clusterUtils() {
        return clusterUtils;
    }

    public Deployment withClusterUtils(Map<Id, ClusterUtilization> clusterUtilization) {
        return new Deployment(zone, applicationVersion, version, deployTime, clusterUtilization, clusterInfo, metrics);
    }

    public Deployment withClusterInfo(Map<Id, ClusterInfo> newClusterInfo) {
        return new Deployment(zone, applicationVersion, version, deployTime, clusterUtils, newClusterInfo, metrics);
    }

    public Deployment withMetrics(DeploymentMetrics metrics) {
        return new Deployment(zone, applicationVersion, version, deployTime, clusterUtils, clusterInfo, metrics);
    }

    /** @return Key metrics for the deployment (application level) like QPS and document count */
    public DeploymentMetrics metrics() {
        return metrics;
    }

    /**
     * Calculate cost for this deployment.
     *
     * This is based on cluster utilization and cluster info.
     */
    public DeploymentCost calculateCost() {

        Map<String, ClusterCost> costClusters = new HashMap<>();
        for (Id clusterId : clusterUtils.keySet()) {

            // Only include cluster cost if we have both cluster utilization and cluster info
            if (clusterInfo.containsKey(clusterId)) {
                costClusters.put(clusterId.value(), new ClusterCost(clusterInfo.get(clusterId),
                        clusterUtils.get(clusterId)));
            }
        }

        return new DeploymentCost(costClusters);
    }

    @Override
    public String toString() {
        return "deployment to " + zone + " of " + applicationVersion + " on version " + version + " at " + deployTime;
    }
}
