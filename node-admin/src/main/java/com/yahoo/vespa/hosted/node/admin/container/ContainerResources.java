// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import java.util.Objects;

/**
 * @author freva
 */
public class ContainerResources {

    public static final ContainerResources UNLIMITED = ContainerResources.from(0, 0, 0);
    public static final int CPU_PERIOD_US = 100_000; // 100 ms

    /**
     * Hard limit on container's CPU usage: Implemented using Completely Fair Scheduler (CFS) by allocating a given
     * time within a given period, Container's processes are not bound to any specific CPU, which may create significant
     * performance degradation as processes are scheduled on another CPU after exhausting the quota.
     */
    private final double cpus;

    /**
     * Soft limit on container's CPU usage:  When plenty of CPU cycles are available, all containers use as much
     * CPU as they need. It prioritizes container CPU resources for the available CPU cycles.
     * It does not guarantee or reserve any specific CPU access.
     */
    private final int cpuShares;

    /** The maximum amount, in bytes, of memory the container can use. */
    private final long memoryBytes;

    public ContainerResources(double cpus, int cpuShares, long memoryBytes) {
        this.cpus = cpus;
        this.cpuShares = cpuShares;
        this.memoryBytes = memoryBytes;

        if (cpus < 0)
            throw new IllegalArgumentException("CPUs must be a positive number or 0 for unlimited, was " + cpus);
        if (cpuShares != 0 && (cpuShares < 2 || cpuShares > 262_144))
            throw new IllegalArgumentException("CPU shares must be a positive integer in [2, 262144] or 0 for unlimited, was " + cpuShares);
        if (memoryBytes < 0)
            throw new IllegalArgumentException("memoryBytes must be a positive integer or 0 for unlimited, was " + memoryBytes);
    }

    /**
     * Create container resources from required fields.
     *
     * @param maxVcpu the amount of vcpu that allocation policies should allocate exclusively to this container.
     *                This is a hard upper limit. To allow an unlimited amount use 0.
     * @param minVcpu the minimal amount of vcpu dedicated to this container.
     *                To avoid dedicating any cpu at all, use 0.
     * @param memoryGb the amount of memory that allocation policies should allocate to this container.
     *                 This is a hard upper limit. To allow the container to allocate an unlimited amount use 0.
     * @return the container resources encapsulating the parameters
     */
    public static ContainerResources from(double maxVcpu, double minVcpu, double memoryGb) {
        return new ContainerResources(maxVcpu,
                                      (int) Math.round(32 * minVcpu),
                                      (long) ((1L << 30) * memoryGb));
    }

    public double cpus() {
        return cpus;
    }

    /** Returns the CFS CPU quota per {@link #cpuPeriod()}, or -1 if disabled. */
    public int cpuQuota() {
        return cpus > 0 ? (int) (cpus * CPU_PERIOD_US) : -1;
    }

    /** Duration (in µs) of a single period used as the basis for process scheduling */
    public int cpuPeriod() {
        return CPU_PERIOD_US;
    }

    public int cpuShares() {
        return cpuShares;
    }

    public long memoryBytes() {
        return memoryBytes;
    }

    /** Returns true iff the memory component(s) of between <code>this</code> and <code>other</code> are equal */
    public boolean equalsMemory(ContainerResources other) {
        return memoryBytes == other.memoryBytes;
    }

    /** Returns true iff the CPU component(s) of between <code>this</code> and <code>other</code> are equal */
    public boolean equalsCpu(ContainerResources other) {
        return Math.abs(other.cpus - cpus) < 0.0001 &&
                // When using CGroups V2, CPU shares (range [2, 262144]) is mapped to CPU weight (range [1, 10000]),
                // because there are ~26.2 shares/weight, we must allow for small deviation in cpuShares
                // when comparing ContainerResources created from NodeResources vs one created from reading the
                // CGroups weight file
                Math.abs(cpuShares - other.cpuShares) < 28;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerResources that = (ContainerResources) o;
        return equalsMemory(that) && equalsCpu(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpus, cpuShares, memoryBytes);
    }


    /** Returns only the memory component(s) of {@link #toString()} */
    public String toStringMemory() {
        return (memoryBytes > 0 ? memoryBytes + "B" : "unlimited") + " memory";
    }

    /** Returns only the CPU component(s) of {@link #toString()} */
    public String toStringCpu() {
        return (cpus > 0 ? String.format("%.2f", cpus) : "unlimited") +" CPUs, " +
                (cpuShares > 0 ? cpuShares : "unlimited") + " CPU Shares";
    }

    @Override
    public String toString() {
        return toStringCpu() + ", " + toStringMemory();
    }

    public ContainerResources withMemoryBytes(long memoryBytes) {
        return new ContainerResources(cpus, cpuShares, memoryBytes);
    }

    public ContainerResources withUnlimitedCpus() {
        return new ContainerResources(0, 0, memoryBytes);
    }
}
