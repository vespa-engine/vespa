// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostCapacity {
    @JsonProperty("vcpu")
    private final double vcpu;

    @JsonProperty("memoryGb")
    private final double memoryGb;

    @JsonProperty("diskGb")
    private final double diskGb;

    @JsonProperty("count")
    private final int count;

    public HostCapacity(@JsonProperty("vcpu") double vcpu,
                        @JsonProperty("memoryGb") double memoryGb,
                        @JsonProperty("diskGb") double diskGb,
                        @JsonProperty("count") int count) {
        this.vcpu = requirePositive("vcpu", vcpu);
        this.memoryGb = requirePositive("memoryGb", memoryGb);
        this.diskGb = requirePositive("diskGb", diskGb);
        this.count = (int) requirePositive("count", count);
    }

    public double getVcpu() {
        return vcpu;
    }

    public double getMemoryGb() {
        return memoryGb;
    }

    public double getDiskGb() {
        return diskGb;
    }

    public int getCount() {
        return count;
    }

    private static double requirePositive(String name, double value) {
        if (value <= 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostCapacity that = (HostCapacity) o;
        return Double.compare(that.vcpu, vcpu) == 0 &&
                Double.compare(that.memoryGb, memoryGb) == 0 &&
                Double.compare(that.diskGb, diskGb) == 0 &&
                count == that.count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vcpu, memoryGb, diskGb, count);
    }
}
