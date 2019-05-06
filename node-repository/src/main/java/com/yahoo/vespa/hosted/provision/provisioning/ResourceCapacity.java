// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;

/**
 * Represent the capacity in terms of physical resources like memory, disk and cpu.
 * Can represent free, aggregate or total capacity of one or several nodes.
 *
 * Immutable.
 *
 * @author smorgrav
 */
public class ResourceCapacity {

    public static final ResourceCapacity NONE = new ResourceCapacity(0, 0, 0);

    private final double memory;
    private final double cpu;
    private final double disk;

    private ResourceCapacity(double memory, double cpu, double disk) {
        this.memory = memory;
        this.cpu = cpu;
        this.disk = disk;
    }

    static ResourceCapacity of(Flavor flavor) {
        return new ResourceCapacity(
                flavor.getMinMainMemoryAvailableGb(), flavor.getMinCpuCores(), flavor.getMinDiskAvailableGb());
    }

    static ResourceCapacity of(NodeResources flavor) {
        return new ResourceCapacity(flavor.memoryGb(), flavor.vcpu(), flavor.diskGb());
    }

    static ResourceCapacity of(Node node) {
        return ResourceCapacity.of(node.flavor());
    }

    public double getMemory() {
        return memory;
    }

    public double getCpu() {
        return cpu;
    }

    public double getDisk() {
        return disk;
    }

    public ResourceCapacity subtract(ResourceCapacity other) {
        return new ResourceCapacity(memory - other.memory,
                cpu - other.cpu,
                disk - other.disk);
    }

    public ResourceCapacity add(ResourceCapacity other) {
        return new ResourceCapacity(memory + other.memory,
                cpu + other.cpu,
                disk + other.disk);
    }

    boolean hasCapacityFor(ResourceCapacity capacity) {
        return memory >= capacity.memory &&
                cpu >= capacity.cpu &&
                disk >= capacity.disk;
    }

    boolean hasCapacityFor(Flavor flavor) {
        return hasCapacityFor(ResourceCapacity.of(flavor));
    }

    int freeCapacityInFlavorEquivalence(Flavor flavor) {
        if (!hasCapacityFor(ResourceCapacity.of(flavor))) return 0;

        double memoryFactor = Math.floor(memory/flavor.getMinMainMemoryAvailableGb());
        double cpuFactor = Math.floor(cpu/flavor.getMinCpuCores());
        double diskFactor =  Math.floor(disk/flavor.getMinDiskAvailableGb());

        return (int) Math.min(Math.min(memoryFactor, cpuFactor), diskFactor);
    }

    /**
     * Normal compare implementation where -1 if this is less than that.
     */
    int compare(ResourceCapacity that) {
        if (memory > that.memory) return 1;
        if (memory < that.memory) return -1;
        if (disk > that.disk) return 1;
        if (disk < that.disk) return -1;
        if (cpu > that.cpu) return 1;
        if (cpu < that.cpu) return -1;
        return 0;
    }
}
