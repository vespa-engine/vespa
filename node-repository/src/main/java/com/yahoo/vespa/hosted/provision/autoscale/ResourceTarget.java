// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

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
    public static ResourceTarget idealLoad(double currentCpuLoad, double currentMemoryLoad, double currentDiskLoad,
                                           AllocatableClusterResources current) {
        return new ResourceTarget(nodeUsage(Resource.cpu, currentCpuLoad, current) / Resource.cpu.idealAverageLoad(),
                                  nodeUsage(Resource.memory, currentMemoryLoad, current) / Resource.memory.idealAverageLoad(),
                                  nodeUsage(Resource.disk, currentDiskLoad, current) / Resource.disk.idealAverageLoad(),
                                  true);
    }

    /** Crete a target of preserving a current allocation */
    public static ResourceTarget preserve(AllocatableClusterResources current) {
        return new ResourceTarget(current.realResources().nodeResources().vcpu(),
                                  current.realResources().nodeResources().memoryGb(),
                                  current.realResources().nodeResources().diskGb(),
                                  false);
    }

}
