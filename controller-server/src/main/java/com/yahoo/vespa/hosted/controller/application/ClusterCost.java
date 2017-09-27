// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

/**
 * Calculate tco and waste for one cluster within one deployment.
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

        this.clusterInfo = clusterInfo;
        this.systemUtilization = systemUtilization;
        this.targetUtilization = new ClusterUtilization(0.7,0.2, 0.7, 0.3);
        this.resultUtilization = calculateResultUtilization(systemUtilization, targetUtilization);

        this.tco = clusterInfo.getFlavor().cost() * Math.min(1, this.resultUtilization.getMaxUtilization());
        this.waste  = clusterInfo.getFlavor().cost() - tco;
    }

    public double getTco() {
        return tco;
    }

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
