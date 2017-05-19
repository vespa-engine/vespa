package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;

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

    public double getMemory() {
        return memory;
    }

    public double getCpu() {
        return cpu;
    }

    public double getDisk() {
        return disk;
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

    boolean hasCapacityFor(Flavor flavor) {
        return memory >= flavor.getMinMainMemoryAvailableGb() &&
                cpu >= flavor.getMinCpuCores() &&
                disk >= flavor.getMinDiskAvailableGb();
    }

    int freeCapacityInFlavorEquivalence(Flavor flavor) {
        if (!hasCapacityFor(flavor)) return 0;

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

    Flavor asFlavor() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("spareflavor", cpu, memory, disk, Flavor.Type.DOCKER_CONTAINER).idealHeadroom(1);
        return new Flavor(b.build().flavor(0));
    }
}
