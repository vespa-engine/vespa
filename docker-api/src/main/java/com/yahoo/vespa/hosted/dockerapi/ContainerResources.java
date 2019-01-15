// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;

/**
 * @author valerijf
 */
public class ContainerResources {
    public static final ContainerResources UNLIMITED = ContainerResources.from(0, 0, 0);
    private static final int CPU_PERIOD = 100_000; // 100 µs

    /** Hard limit on container's CPU usage: Implemented using Completely Fair Scheduler (CFS) by allocating a given
     * time within a given period, Container's processes are not bound to any specific CPU, which may create significant
     * performance degradation as processes are scheduled on another CPU after exhausting the quota. */
    private final double cpus;

    /** Soft limit on container's CPU usage:  When plenty of CPU cycles are available, all containers use as much
     * CPU as they need. It prioritizes container CPU resources for the available CPU cycles.
     * It does not guarantee or reserve any specific CPU access. */
    private final int cpuShares;

    /** The maximum amount, in bytes, of memory the container can use. */
    private final long memoryBytes;

    ContainerResources(double cpus, int cpuShares, long memoryBytes) {
        this.cpus = cpus;
        this.cpuShares = cpuShares;
        this.memoryBytes = memoryBytes;

        if (cpus < 0)
            throw new IllegalArgumentException("CPUs must be a positive number or 0 for unlimited, was " + cpus);
        if (cpuShares < 0)
            throw new IllegalArgumentException("CPU shares must be a positive integer or 0 for unlimited, was " + cpuShares);
        if (memoryBytes < 0)
            throw new IllegalArgumentException("memoryBytes must be a positive integer or 0 for unlimited, was " + memoryBytes);
    }

    public static ContainerResources from(double cpus, double cpuCores, double memoryGb) {
        return new ContainerResources(
                cpus,
                (int) Math.round(10 * cpuCores),
                (long) ((1L << 30) * memoryGb));
    }

    public double cpus() {
        return cpus;
    }

    public int cpuQuota() {
        return (int) (cpus * CPU_PERIOD);
    }

    public int cpuPeriod() {
        return CPU_PERIOD;
    }

    public int cpuShares() {
        return cpuShares;
    }

    public long memoryBytes() {
        return memoryBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerResources that = (ContainerResources) o;
        return Math.abs(that.cpus - cpus) < 0.0001 &&
                cpuShares == that.cpuShares &&
                memoryBytes == that.memoryBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpus, cpuShares, memoryBytes);
    }

    @Override
    public String toString() {
        return (cpus > 0 ? cpus : "unlimited") +" CPUs, " +
                (cpuShares > 0 ? cpuShares : "unlimited") + " CPU Shares, " +
                (memoryBytes > 0 ? memoryBytes + "B" : "unlimited") + " memory";
    }
}
