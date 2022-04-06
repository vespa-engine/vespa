// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.NodeResources;

import java.util.Objects;

/**
 * An allocation of node resources.
 *
 * @author ldalves
 */
public class ResourceAllocation {

    public static final ResourceAllocation ZERO = new ResourceAllocation(0, 0, 0, NodeResources.Architecture.getDefault());

    private final double cpuCores;
    private final double memoryGb;
    private final double diskGb;
    private final NodeResources.Architecture architecture;

    public ResourceAllocation(double cpuCores, double memoryGb, double diskGb, NodeResources.Architecture architecture) {
        this.cpuCores = cpuCores;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.architecture = architecture;
    }

    public double usageFraction(ResourceAllocation total) {
        return (cpuCores / total.cpuCores + memoryGb / total.memoryGb + diskGb / total.diskGb) / 3;
    }

    public double getCpuCores() {
        return cpuCores;
    }

    public double getMemoryGb() {
        return memoryGb;
    }

    public double getDiskGb() {
        return diskGb;
    }

    public NodeResources.Architecture getArchitecture() {
        return architecture;
    }

    /** Returns a copy of this with the given allocation added */
    public ResourceAllocation plus(ResourceAllocation allocation) {
        return new ResourceAllocation(cpuCores + allocation.cpuCores, memoryGb + allocation.memoryGb, diskGb + allocation.diskGb, architecture);
    }

    /** Returns a copy of this with each resource multiplied by given factor */
    public ResourceAllocation multiply(double multiplicand) {
        return new ResourceAllocation(cpuCores * multiplicand, memoryGb * multiplicand, diskGb * multiplicand, architecture);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceAllocation)) return false;

        ResourceAllocation other = (ResourceAllocation) o;
        return Double.compare(this.cpuCores, other.cpuCores) == 0 &&
                Double.compare(this.memoryGb, other.memoryGb) == 0 &&
                Double.compare(this.diskGb, other.diskGb) == 0;

    }

    @Override
    public int hashCode() {
        return Objects.hash(cpuCores, memoryGb, diskGb);
    }

}

