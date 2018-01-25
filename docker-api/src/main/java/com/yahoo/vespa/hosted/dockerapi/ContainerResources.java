// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

/**
 * @author valerijf
 */
public class ContainerResources {
    public static final ContainerResources UNLIMITED = ContainerResources.from(0, 0);

    public final int cpuShares;
    public final long memoryBytes;

    ContainerResources(int cpuShares, long memoryBytes) {
        this.cpuShares = cpuShares;
        this.memoryBytes = memoryBytes;
    }

    public static ContainerResources from(double cpuCores, double memoryGb) {
        return new ContainerResources(
                (int) Math.round(10 * cpuCores),
                (long) ((1L << 30) * memoryGb));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ContainerResources that = (ContainerResources) o;

        if (cpuShares != that.cpuShares) return false;
        return memoryBytes == that.memoryBytes;
    }

    @Override
    public int hashCode() {
        int result = cpuShares;
        result = 31 * result + (int) (memoryBytes ^ (memoryBytes >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return cpuShares + " CPU Shares, " + memoryBytes + "B memory";
    }
}
