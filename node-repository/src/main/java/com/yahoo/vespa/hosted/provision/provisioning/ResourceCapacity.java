// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;

/**
 * Represent the capacity in terms of physical resources like memory, disk and cpu.
 * Can represent free, aggregate or total capacity of one or several nodes.
 *
 * @author smorgrav
 */
public class ResourceCapacity {

    private double memory;
    private double cpu;
    private double disk;

    ResourceCapacity() {
        memory = 0;
        cpu = 0;
        disk = 0;
    }

    ResourceCapacity(Node node) {
        memory = node.flavor().getMinMainMemoryAvailableGb();
        cpu = node.flavor().getMinCpuCores();
        disk = node.flavor().getMinDiskAvailableGb();
    }

    static ResourceCapacity of(Flavor flavor) {
        ResourceCapacity capacity = new ResourceCapacity();
        capacity.memory = flavor.getMinMainMemoryAvailableGb();
        capacity.cpu = flavor.getMinCpuCores();
        capacity.disk = flavor.getMinDiskAvailableGb();
        return capacity;
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

    static ResourceCapacity composite(ResourceCapacity a, ResourceCapacity b) {
        ResourceCapacity composite = new ResourceCapacity();
        composite.memory = a.memory + b.memory;
        composite.cpu -= a.cpu + b.cpu;
        composite.disk -=  a.disk + b.disk;

        return composite;
    }

    void subtract(Node node) {
        memory -= node.flavor().getMinMainMemoryAvailableGb();
        cpu -= node.flavor().getMinCpuCores();
        disk -= node.flavor().getMinDiskAvailableGb();
    }

    public static ResourceCapacity add(ResourceCapacity a, ResourceCapacity b) {
        ResourceCapacity result = new ResourceCapacity();
        result.memory = a.memory + b.memory;
        result.cpu = a.cpu + b.cpu;
        result.disk = a.disk + b.disk;
        return result;
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

        double aggregateFactor = Math.min(memoryFactor, cpuFactor);
        aggregateFactor = Math.min(aggregateFactor, diskFactor);

        return (int)aggregateFactor;
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
