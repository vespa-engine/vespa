package com.yahoo.vespa.hosted.provision.applications;

import java.util.Objects;

/**
 * When there are multiple deployments of an application in different regions,
 * instances of the cluster across regions may form a "BCP group".
 * By default the clusters in all production regions form such a group, but other arrangements
 * may be specified in deployment.xml, see com.yahoo.config.application.api.Bcp.
 *
 * This contains metrics averaged over the other clusters in the group this belongs to,
 * which is used to amend scaling decisions in this cluster when it has little traffic on its own.
 *
 * @author bratseth
 */
public class BcpGroupInfo {

    private static final BcpGroupInfo empty = new BcpGroupInfo(0, 0, 0);

    private final double queryRate;
    private final double growthRateHeadroom;
    private final double cpuCostPerQuery;

    public BcpGroupInfo(double queryRate, double growthRateHeadroom, double cpuCostPerQuery) {
        this.queryRate = queryRate;
        this.growthRateHeadroom = growthRateHeadroom;
        this.cpuCostPerQuery = cpuCostPerQuery;
    }

    /** Returns the max peak query rate (queries/second) of the other clusters in the group this belongs to. */
    public double queryRate() { return queryRate; }

    /** Returns the average growth rate headroom of the other clusters in the group this belongs to. */
    public double growthRateHeadroom() { return growthRateHeadroom; }

    /** Returns the average total cluster CPU cost per query of the other clusters in the group this belongs to. */
    public double cpuCostPerQuery() { return cpuCostPerQuery; }

    public boolean isEmpty() {
        return queryRate == 0 && growthRateHeadroom == 0 && cpuCostPerQuery == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof BcpGroupInfo other)) return false;
        if ( other.queryRate != this.queryRate) return false;
        if ( other.growthRateHeadroom != this.growthRateHeadroom) return false;
        if ( other.cpuCostPerQuery != this.cpuCostPerQuery) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryRate, growthRateHeadroom, cpuCostPerQuery);
    }

    @Override
    public String toString() {
        return "BCP group info: " + queryRate + " q/s, " + growthRateHeadroom + " q/s headroom, " +
               cpuCostPerQuery + " CPU cost per q/s";
    }

    public static BcpGroupInfo empty() { return empty; }

}
