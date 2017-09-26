// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

/**
 * Calculate tco and waste for one cluster within one Vespa application in one zone.
 *
 * @author smorgrav
 */
public class CostCluster {
    private final double tco;
    private final double waste;
    private final CostClusterInfo clusterInfo;
    private final CostResources systemUtilization;
    private final CostResources targetUtilization;
    private final CostResources resultUtilization;

    /**
     * @param clusterInfo       Value object with cluster info e.g. the TCO for the hardware used
     * @param systemUtilization Utilization of system resources (as ratios)
     * @param targetUtilization Target utilization (ratios - usually less than 1.0)
     */
    public CostCluster(CostClusterInfo clusterInfo,
                       CostResources systemUtilization,
                       CostResources targetUtilization) {

        this.clusterInfo = clusterInfo;
        this.systemUtilization = systemUtilization;
        this.targetUtilization = targetUtilization;
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

    public CostClusterInfo getClusterInfo() {
        return clusterInfo;
    }

    public CostResources getSystemUtilization() {
        return systemUtilization;
    }

    public CostResources getTargetUtilization() {
        return targetUtilization;
    }

    public CostResources getResultUtilization() {
        return resultUtilization;
    }

    static CostResources calculateResultUtilization(CostResources system, CostResources target) {
        double cpu = ratio(system.getCpu(),target.getCpu());
        double mem = ratio(system.getMemory(),target.getMemory());
        double disk = ratio(system.getDisk(),target.getDisk());
        double diskbusy = ratio(system.getDiskBusy(),target.getDiskBusy());

        return new CostResources(mem, cpu, disk, diskbusy);
    }

    private static double ratio(double a, double b) {
        if (b == 0) return 1;
        return a/b;
    }
}
