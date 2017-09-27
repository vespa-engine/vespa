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
 */
public class Deployment {

    private final Zone zone;
    private final ApplicationRevision revision;
    private final Version version;
    private final Instant deployTime;
    private final Map<Id, ClusterUtilization> clusterUtils = new HashMap<>();
    private final Map<Id, ClusterInfo> clusterInfo = new HashMap<>();

    public Deployment(Zone zone, ApplicationRevision revision, Version version, Instant deployTime) {
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(revision, "revision cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(deployTime, "deployTime cannot be null");
        this.zone = zone;
        this.revision = revision;
        this.version = version;
        this.deployTime = deployTime;
    }

    /** Returns the zone this was deployed to */
    public Zone zone() { return zone; }

    /** Returns the revision of the application which was deployed */
    public ApplicationRevision revision() { return revision; }
    
    /** Returns the Vespa version which was deployed */
    public Version version() { return version; }

    /** Returns the time this was deployed */
    public Instant at() { return deployTime; }

    public Map<Id, ClusterUtilization> getClusterUtilization() {
        return clusterUtils;
    }

    public Map<Id, ClusterInfo> getClusterInfo() {
        return clusterInfo;
    }

    /**
     * Calculate cost for this deployment.
     *
     * This is based on cluster utilization and cluster info.
     */
    public DeploymentCost calculateCost() {

        Map<String, ClusterCost> costClusters = new HashMap<>();
        for (Id clusterId : clusterUtils.keySet()) {
            costClusters.put(clusterId.value(), new ClusterCost(clusterInfo.get(clusterId), clusterUtils.get(clusterId)));
        }

        return new DeploymentCost(costClusters);
    }

    @Override
    public String toString() {
        return "deployment to " + zone + " of " + revision + " on version " + version + " at " + deployTime;
    }
}
