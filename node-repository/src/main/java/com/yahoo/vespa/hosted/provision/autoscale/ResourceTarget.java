// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.applications.Application;

import java.time.Duration;

/**
 * A resource target to hit for the allocation optimizer.
 * The target is measured in cpu, memory and disk per node in the allocation given by current.
 *
 * @author bratseth
 */
public class ResourceTarget {

    private final boolean adjustForRedundancy;

    /** The target real resources per node, assuming the node assignment where this was decided */
    private final double cpu, memory, disk;

    private ResourceTarget(double cpu, double memory, double disk, boolean adjustForRedundancy) {
        this.cpu = cpu;
        this.memory = memory;
        this.disk = disk;
        this.adjustForRedundancy = adjustForRedundancy;
    }

    /** Are the target resources given by this including redundancy or not */
    public boolean adjustForRedundancy() { return adjustForRedundancy; }
    
    /** Returns the target cpu per node, in terms of the current allocation */
    public double nodeCpu() { return cpu; }

    /** Returns the target memory per node, in terms of the current allocation */
    public double nodeMemory() { return memory; }

    /** Returns the target disk per node, in terms of the current allocation */
    public double nodeDisk() { return disk; }

    @Override
    public String toString() {
        return "target " +
               (adjustForRedundancy ? "(with redundancy adjustment) " : "") +
               "[vcpu " + cpu + ", memoryGb " + memory + ", diskGb " + disk + "]";
    }

    private static double nodeUsage(Resource resource, double load, AllocatableClusterResources current) {
        return load * resource.valueFrom(current.realResources().nodeResources());
    }

    /** Create a target of achieving ideal load given a current load */
    public static ResourceTarget idealLoad(Duration scalingDuration,
                                           ClusterTimeseries clusterTimeseries,
                                           ClusterNodesTimeseries clusterNodesTimeseries,
                                           AllocatableClusterResources current,
                                           Application application) {
        return new ResourceTarget(nodeUsage(Resource.cpu, clusterNodesTimeseries.averageLoad(Resource.cpu), current)
                                  / idealCpuLoad(scalingDuration, clusterTimeseries, application),
                                  nodeUsage(Resource.memory, clusterNodesTimeseries.averageLoad(Resource.memory), current)
                                  / Resource.memory.idealAverageLoad(),
                                  nodeUsage(Resource.disk, clusterNodesTimeseries.averageLoad(Resource.disk), current)
                                  / Resource.disk.idealAverageLoad(),
                                  true);
    }

    /** Crete a target of preserving a current allocation */
    public static ResourceTarget preserve(AllocatableClusterResources current) {
        return new ResourceTarget(current.realResources().nodeResources().vcpu(),
                                  current.realResources().nodeResources().memoryGb(),
                                  current.realResources().nodeResources().diskGb(),
                                  false);
    }

    /** Ideal cpu load must take the application traffic fraction into account */
    public static double idealCpuLoad(Duration scalingDuration,
                                      ClusterTimeseries clusterTimeseries,
                                      Application application) {
        double queryCpuFraction = queryCpuFraction(clusterTimeseries);

        // What's needed to have headroom for growth during scale-up as a fraction of current resources?
        double maxGrowthRate = clusterTimeseries.maxQueryGrowthRate(); // in fraction per minute of the current traffic
        double growthRateHeadroom = 1 + maxGrowthRate * scalingDuration.toMinutes();
        // Cap headroom at 10% above the historical observed peak
        double fractionOfMax = clusterTimeseries.currentQueryFractionOfMax();
        if (fractionOfMax != 0)
            growthRateHeadroom = Math.min(growthRateHeadroom, 1 / fractionOfMax + 0.1);

        // How much headroom is needed to handle sudden arrival of additional traffic due to another zone going down?
        double maxTrafficShiftHeadroom = 10.0; // Cap to avoid extreme sizes from a current very small share
        double trafficShiftHeadroom;
        if (application.status().maxReadShare() == 0) // No traffic fraction data
            trafficShiftHeadroom = 2.0; // assume we currently get half of the global share of traffic
        else if (application.status().currentReadShare() == 0)
            trafficShiftHeadroom = maxTrafficShiftHeadroom;
        else
            trafficShiftHeadroom = application.status().maxReadShare() / application.status().currentReadShare();
        trafficShiftHeadroom = Math.min(trafficShiftHeadroom, maxTrafficShiftHeadroom);

        // Assumptions: 1) Write load is not organic so we should not grow to handle more.
        //                 (TODO: But allow applications to set their target write rate and size for that)
        //              2) Write load does not change in BCP scenarios.
        return queryCpuFraction * 1 / growthRateHeadroom * 1 / trafficShiftHeadroom * idealQueryCpuLoad() +
               (1 - queryCpuFraction) * idealWriteCpuLoad();
    }
    
    private static double queryCpuFraction(ClusterTimeseries clusterTimeseries) {
        double queryRate = clusterTimeseries.currentQueryRate();
        double writeRate = clusterTimeseries.currentWriteRate();
        if (queryRate == 0 && writeRate == 0) return queryCpuFraction(0.5);
        return queryCpuFraction(queryRate / (queryRate + writeRate));
    }

    private static double queryCpuFraction(double queryFraction) {
        double relativeQueryCost = 9; // How much more expensive are queries than writes? TODO: Measure
        double writeFraction = 1 - queryFraction;
        return queryFraction * relativeQueryCost / (queryFraction * relativeQueryCost + writeFraction);
    }

    public static double idealQueryCpuLoad() { return Resource.cpu.idealAverageLoad(); }

    public static double idealWriteCpuLoad() { return 0.95; }

    public static double idealMemoryLoad() { return Resource.memory.idealAverageLoad(); }

    public static double idealDiskLoad() { return Resource.disk.idealAverageLoad(); }

}
