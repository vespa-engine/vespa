// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

class LoadBasedResourceTarget extends ResourceTarget {

    private final double cpuLoad;
    private final double memoryLoad;
    private final double diskLoad;
    private final AllocatableClusterResources allocation;

    public LoadBasedResourceTarget(double cpuLoad, double memoryLoad, double diskLoad,
                                   AllocatableClusterResources sourceAllocation) {
        super(sourceAllocation.nodes(), sourceAllocation.groups());
        this.cpuLoad = cpuLoad;
        this.memoryLoad = memoryLoad;
        this.diskLoad = diskLoad;
        this.allocation = sourceAllocation;
    }

    @Override
    public double clusterCpu() {
        return clusterUsage(Resource.cpu, cpuLoad) / Resource.cpu.idealAverageLoad();
    }

    @Override
    public double groupMemory() {
        return groupUsage(Resource.memory, memoryLoad) / Resource.memory.idealAverageLoad();
    }

    @Override
    public double groupDisk() {
        return groupUsage(Resource.disk, diskLoad) / Resource.disk.idealAverageLoad();
    }

    @Override
    public double nodeMemory() {
        return nodeUsage(Resource.memory, memoryLoad) / Resource.memory.idealAverageLoad();
    }

    @Override
    public double nodeDisk() {
        return nodeUsage(Resource.disk, diskLoad) / Resource.disk.idealAverageLoad();
    }

    private double clusterUsage(Resource resource, double load) {
        return nodeUsage(resource, load) * allocation.nodes();
    }

    private double groupUsage(Resource resource, double load) {
        return nodeUsage(resource, load) * sourceGroupSize();
    }

    private double nodeUsage(Resource resource, double load) {
        return load * resource.valueFrom(allocation.realResources());
    }

}
