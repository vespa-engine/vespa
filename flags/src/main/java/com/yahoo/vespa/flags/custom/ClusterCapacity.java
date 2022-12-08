// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author freva
 */
// @Immutable
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class ClusterCapacity {
    private final int count;
    private final OptionalDouble vcpu;
    private final OptionalDouble memoryGb;
    private final OptionalDouble diskGb;
    private final OptionalDouble bandwidthGbps;
    private final Optional<String> diskSpeed;
    private final Optional<String> storageType;
    private final Optional<String> architecture;


    @JsonCreator
    public ClusterCapacity(@JsonProperty("count") int count,
                           @JsonProperty("vcpu") Double vcpu,
                           @JsonProperty("memoryGb") Double memoryGb,
                           @JsonProperty("diskGb") Double diskGb,
                           @JsonProperty("bandwidthGbps") Double bandwidthGbps,
                           @JsonProperty("diskSpeed") String diskSpeed,
                           @JsonProperty("storageType") String storageType,
                           @JsonProperty("architecture") String architecture) {
        this.count = (int) requireNonNegative("count", count);
        this.vcpu = vcpu == null ? OptionalDouble.empty() : OptionalDouble.of(requireNonNegative("vcpu", vcpu));
        this.memoryGb = memoryGb == null ? OptionalDouble.empty() : OptionalDouble.of(requireNonNegative("memoryGb", memoryGb));
        this.diskGb = diskGb == null ? OptionalDouble.empty() : OptionalDouble.of(requireNonNegative("diskGb", diskGb));
        this.bandwidthGbps = bandwidthGbps == null ? OptionalDouble.empty() : OptionalDouble.of(bandwidthGbps);
        this.diskSpeed = Optional.ofNullable(diskSpeed);
        this.storageType = Optional.ofNullable(storageType);
        this.architecture = Optional.ofNullable(architecture);
    }

    /** Returns a new ClusterCapacity equal to {@code this}, but with the given count. */
    public ClusterCapacity withCount(int count) {
        return new ClusterCapacity(count, vcpuOrNull(), memoryGbOrNull(), diskGbOrNull(), bandwidthGbpsOrNull(),
                                   diskSpeedOrNull(), storageTypeOrNull(), architectureOrNull());
    }

    @JsonGetter("count") public int count() { return count; }
    @JsonGetter("vcpu") public Double vcpuOrNull() {
        return vcpu.isPresent() ? vcpu.getAsDouble() : null;
    }
    @JsonGetter("memoryGb") public Double memoryGbOrNull() {
        return memoryGb.isPresent() ? memoryGb.getAsDouble() : null;
    }
    @JsonGetter("diskGb") public Double diskGbOrNull() {
        return diskGb.isPresent() ? diskGb.getAsDouble() : null;
    }
    @JsonGetter("bandwidthGbps") public Double bandwidthGbpsOrNull() {
        return bandwidthGbps.isPresent() ? bandwidthGbps.getAsDouble() : null;
    }
    @JsonGetter("diskSpeed") public String diskSpeedOrNull() {
        return diskSpeed.orElse(null);
    }
    @JsonGetter("storageType") public String storageTypeOrNull() {
        return storageType.orElse(null);
    }
    @JsonGetter("architecture") public String architectureOrNull() {
        return architecture.orElse(null);
    }

    @JsonIgnore public Double vcpu() { return vcpu.orElse(0.0); }
    @JsonIgnore public Double memoryGb() { return memoryGb.orElse(0.0); }
    @JsonIgnore public Double diskGb() { return diskGb.orElse(0.0); }
    @JsonIgnore public double bandwidthGbps() { return bandwidthGbps.orElse(1.0); }
    @JsonIgnore public String diskSpeed() { return diskSpeed.orElse("fast"); }
    @JsonIgnore public String storageType() { return storageType.orElse("any"); }
    @JsonIgnore public String architecture() { return architecture.orElse("x86_64"); }

    @Override
    public String toString() {
        return "ClusterCapacity{" +
                "count=" + count +
                ", vcpu=" + vcpu +
                ", memoryGb=" + memoryGb +
                ", diskGb=" + diskGb +
                ", bandwidthGbps=" + bandwidthGbps +
                ", diskSpeed=" + diskSpeed +
                ", storageType=" + storageType +
                ", architecture=" + architecture +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterCapacity that = (ClusterCapacity) o;
        return count == that.count &&
                vcpu.equals(that.vcpu) &&
                memoryGb.equals(that.memoryGb) &&
                diskGb.equals(that.diskGb) &&
                bandwidthGbps.equals(that.bandwidthGbps) &&
                diskSpeed.equals(that.diskSpeed) &&
                storageType.equals(that.storageType) &&
                architecture.equals(that.architecture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType, architecture);
    }

    private static double requireNonNegative(String name, double value) {
        if (value < 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }

}
