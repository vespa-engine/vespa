// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.util.Objects;

/**
 * Calculate utilization relative to the target utilization,
 * tco and waste for one cluster of one deployment.
 *
 * The target utilization is defined the following assumptions:
 * 1. CPU contention starts to cause problems at 0.8
 * 2. Memory management starts to cause problems at 0.7
 * 3. Load is evenly divided between two deployments - each deployments can handle the other.
 * 4. Memory and disk are agnostic to query load.
 * 5. Peak utilization (daily variations) are twice the size of the average.
 *
 * With this in mind we get:
 *
 * CPU: 0.8/2/2 = 0.2
 * Mem: 0.7
 * Disk: 0.7
 * Disk busy: 0.3
 *
 * @author smorgrav
 */
public class ClusterCost {
    private final double tco;
    private final double waste;
    private final ClusterInfo clusterInfo;
    private final ClusterUtilization systemUtilization;
    private final ClusterUtilization targetUtilization;
    private final ClusterUtilization resultUtilization;

    /**
     * @param clusterInfo       Value object with cluster info e.g. the TCO for the hardware used
     * @param systemUtilization Utilization of system resources (as ratios)
     */
    public ClusterCost(ClusterInfo clusterInfo,
                       ClusterUtilization systemUtilization) {

        Objects.requireNonNull(clusterInfo, "Cluster info cannot be null");
        Objects.requireNonNull(systemUtilization, "Cluster utilization cannot be null");

        this.clusterInfo = clusterInfo;
        this.systemUtilization = systemUtilization;
        this.targetUtilization = new ClusterUtilization(0.7,0.2, 0.7, 0.3);
        this.resultUtilization = calculateResultUtilization(systemUtilization, targetUtilization);

        this.tco = clusterInfo.getHostnames().size() * clusterInfo.getFlavorCost();

        double unusedUtilization = 1 - Math.min(1, resultUtilization.getMaxUtilization());
        this.waste  = tco * unusedUtilization;
    }

    /** @return The TCO in dollars for this cluster (node tco * nodes) */
    public double getTco() {
        return tco;
    }

    /** @return The amount of dollars spent for unused resources in this cluster */
    public double getWaste() {
        return waste;
    }

    public ClusterInfo getClusterInfo() {
        return clusterInfo;
    }

    public ClusterUtilization getSystemUtilization() {
        return systemUtilization;
    }

    public ClusterUtilization getTargetUtilization() {
        return targetUtilization;
    }

    public ClusterUtilization getResultUtilization() {
        return resultUtilization;
    }

    static ClusterUtilization calculateResultUtilization(ClusterUtilization system, ClusterUtilization target) {
        double cpu = ratio(system.getCpu(),target.getCpu());
        double mem = ratio(system.getMemory(),target.getMemory());
        double disk = ratio(system.getDisk(),target.getDisk());
        double diskbusy = ratio(system.getDiskBusy(),target.getDiskBusy());

        return new ClusterUtilization(mem, cpu, disk, diskbusy);
    }

    private static double ratio(double a, double b) {
        if (b == 0) return 1;
        return a/b;
    }
}
