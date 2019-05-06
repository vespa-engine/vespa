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

    private final double memoryGb;
    private final double vcpu;
    private final double diskGb;

    private ResourceCapacity(double memoryGb, double vcpu, double diskGb) {
        this.memoryGb = memoryGb;
        this.vcpu = vcpu;
        this.diskGb = diskGb;
    }

    static ResourceCapacity of(Flavor flavor) {
        return new ResourceCapacity(
                flavor.getMinMainMemoryAvailableGb(), flavor.getMinCpuCores(), flavor.getMinDiskAvailableGb());
    }

    static ResourceCapacity of(NodeResources resources) {
        return new ResourceCapacity(resources.memoryGb(), resources.vcpu(), resources.diskGb());
    }

    static ResourceCapacity of(Node node) {
        return ResourceCapacity.of(node.flavor());
    }

    public double memoryGb() {
        return memoryGb;
    }

    public double vcpu() {
        return vcpu;
    }

    public double diskGb() {
        return diskGb;
    }

    public ResourceCapacity subtract(ResourceCapacity other) {
        return new ResourceCapacity(memoryGb - other.memoryGb,
                                    vcpu - other.vcpu,
                                    diskGb - other.diskGb);
    }

    public ResourceCapacity add(ResourceCapacity other) {
        return new ResourceCapacity(memoryGb + other.memoryGb,
                                    vcpu + other.vcpu,
                                    diskGb + other.diskGb);
    }

    boolean hasCapacityFor(ResourceCapacity capacity) {
        return memoryGb >= capacity.memoryGb &&
               vcpu >= capacity.vcpu &&
               diskGb >= capacity.diskGb;
    }

    boolean hasCapacityFor(Flavor flavor) {
        return hasCapacityFor(ResourceCapacity.of(flavor));
    }

}
