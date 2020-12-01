// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * @author freva
 */
// @Immutable
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class ClusterCapacity {
    private final int count;
    private final double vcpu;
    private final double memoryGb;
    private final double diskGb;
    private final OptionalDouble bandwidthGbps;

    @JsonCreator
    public ClusterCapacity(@JsonProperty("count") int count,
                           @JsonProperty("vcpu") double vcpu,
                           @JsonProperty("memoryGb") double memoryGb,
                           @JsonProperty("diskGb") double diskGb,
                           @JsonProperty("bandwidthGbps") Double bandwidthGbps) {
        this.count = (int) requireNonNegative("count", count);
        this.vcpu = requireNonNegative("vcpu", vcpu);
        this.memoryGb = requireNonNegative("memoryGb", memoryGb);
        this.diskGb = requireNonNegative("diskGb", diskGb);
        this.bandwidthGbps = bandwidthGbps == null ? OptionalDouble.empty() : OptionalDouble.of(bandwidthGbps);
    }

    /** Returns a new ClusterCapacity equal to {@code this}, but with the given count. */
    public ClusterCapacity withCount(int count) {
        return new ClusterCapacity(count, vcpu, memoryGb, diskGb, bandwidthGbpsOrNull());
    }

    @JsonGetter("count") public int count() { return count; }
    @JsonGetter("vcpu") public double vcpu() { return vcpu; }
    @JsonGetter("memoryGb") public double memoryGb() { return memoryGb; }
    @JsonGetter("diskGb") public double diskGb() { return diskGb; }
    @JsonGetter("bandwidthGbps") public Double bandwidthGbpsOrNull() {
        return bandwidthGbps.isPresent() ? bandwidthGbps.getAsDouble() : null;
    }

    @JsonIgnore
    public double bandwidthGbps() { return bandwidthGbps.orElse(1.0); }

    @Override
    public String toString() {
        return "ClusterCapacity{" +
                "count=" + count +
                ", vcpu=" + vcpu +
                ", memoryGb=" + memoryGb +
                ", diskGb=" + diskGb +
                ", bandwidthGbps=" + bandwidthGbps +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterCapacity that = (ClusterCapacity) o;
        return count == that.count &&
                Double.compare(that.vcpu, vcpu) == 0 &&
                Double.compare(that.memoryGb, memoryGb) == 0 &&
                Double.compare(that.diskGb, diskGb) == 0 &&
                bandwidthGbps.equals(that.bandwidthGbps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, vcpu, memoryGb, diskGb, bandwidthGbps);
    }

    private static double requireNonNegative(String name, double value) {
        if (value < 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }
}
