// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

public class ResourceTarget {

    /** The target resources per node */
    private final double cpu, memory, disk;

    /** The current allocation leading to this target */
    private final AllocatableClusterResources current;

    private ResourceTarget(double cpu, double memory, double disk, AllocatableClusterResources current) {
        this.cpu = cpu;
        this.memory = memory;
        this.disk = disk;
        this.current = current;
    }

    /** Returns the target total cpu to allocate to the entire cluster */
    public double clusterCpu() { return nodeCpu() * current.nodes(); }

    /** Returns the target total memory to allocate to each group */
    public double groupMemory() { return nodeMemory() * current.groupSize(); }

    /** Returns the target total disk to allocate to each group */
    public double groupDisk() { return nodeDisk() * current.groupSize(); }

    /** Returns the target cpu per node, in terms of the current allocation */
    public double nodeCpu() { return cpu; }

    /** Returns the target memory per node, in terms of the current allocation */
    public double nodeMemory() { return memory; }

    /** Returns the target disk per node, in terms of the current allocation */
    public double nodeDisk() { return disk; }

    private static double nodeUsage(Resource resource, double load, AllocatableClusterResources current) {
        return load * resource.valueFrom(current.realResources());
    }

    /** Create a target of achieving ideal load given a current load */
    public static ResourceTarget idealLoad(double currentCpuLoad, double currentMemoryLoad, double currentDiskLoad,
                                           AllocatableClusterResources current) {
        return new ResourceTarget(nodeUsage(Resource.cpu, currentCpuLoad, current) / Resource.cpu.idealAverageLoad(),
                                  nodeUsage(Resource.memory, currentMemoryLoad, current) / Resource.memory.idealAverageLoad(),
                                  nodeUsage(Resource.disk, currentDiskLoad, current) / Resource.disk.idealAverageLoad(),
                                  current);
    }

    /** Creta a target of preserving a current allocation */
    public static ResourceTarget preserve(AllocatableClusterResources current) {
        return new ResourceTarget(current.toAdvertisedClusterResources().nodeResources().vcpu(),
                                  current.toAdvertisedClusterResources().nodeResources().memoryGb(),
                                  current.toAdvertisedClusterResources().nodeResources().diskGb(),
                                  current);
    }

}
