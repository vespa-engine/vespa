// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec.Id;
import com.yahoo.config.provision.Zone;

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

    private final Zone zone;
    private final ApplicationRevision revision;
    private final Version version;
    private final Instant deployTime;
    private final Map<Id, ClusterUtilization> clusterUtils;
    private final Map<Id, ClusterInfo> clusterInfo;
    private final DeploymentMetrics metrics;

    public Deployment(Zone zone, ApplicationRevision revision, Version version, Instant deployTime) {
        this(zone, revision, version, deployTime, new HashMap<>(), new HashMap<>(), new DeploymentMetrics());
    }

    public Deployment(Zone zone, ApplicationRevision revision, Version version, Instant deployTime,
                      Map<Id, ClusterUtilization> clusterUtils, Map<Id, ClusterInfo> clusterInfo, DeploymentMetrics metrics) {
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(revision, "revision cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(deployTime, "deployTime cannot be null");
        Objects.requireNonNull(clusterUtils, "clusterUtils cannot be null");
        Objects.requireNonNull(clusterInfo, "clusterInfo cannot be null");
        Objects.requireNonNull(metrics, "deployment metrics cannot be null");
        this.zone = zone;
        this.revision = revision;
        this.version = version;
        this.deployTime = deployTime;
        this.clusterUtils = clusterUtils;
        this.clusterInfo = clusterInfo;
        this.metrics = metrics;
    }

    /** Returns the zone this was deployed to */
    public Zone zone() { return zone; }

    /** Returns the revision of the application which was deployed */
    public ApplicationRevision revision() { return revision; }
    
    /** Returns the Vespa version which was deployed */
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
        return new Deployment(zone, revision, version, deployTime, clusterUtilization, clusterInfo, metrics);
    }

    public Deployment withClusterInfo(Map<Id, ClusterInfo> newClusterInfo) {
        return new Deployment(zone, revision, version, deployTime, clusterUtils, newClusterInfo, metrics);
    }

    public Deployment withMetrics(DeploymentMetrics metrics) {
        return new Deployment(zone, revision, version, deployTime, clusterUtils, clusterInfo, metrics);
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
        return "deployment to " + zone + " of " + revision + " on version " + version + " at " + deployTime;
    }
}
