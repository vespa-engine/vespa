// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.OptionalDouble;

import static com.yahoo.vespa.flags.custom.Validation.requireNonNegative;
import static com.yahoo.vespa.flags.custom.Validation.validArchitectures;
import static com.yahoo.vespa.flags.custom.Validation.validClusterTypes;
import static com.yahoo.vespa.flags.custom.Validation.validDiskSpeeds;
import static com.yahoo.vespa.flags.custom.Validation.validStorageTypes;
import static com.yahoo.vespa.flags.custom.Validation.validateEnum;

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
    private final String diskSpeed;
    private final String storageType;
    private final String architecture;
    private final String clusterType;

    @JsonCreator
    public ClusterCapacity(@JsonProperty("count") Integer count,
                           @JsonProperty("vcpu") Double vcpu,
                           @JsonProperty("memoryGb") Double memoryGb,
                           @JsonProperty("diskGb") Double diskGb,
                           @JsonProperty("bandwidthGbps") Double bandwidthGbps,
                           @JsonProperty("diskSpeed") String diskSpeed,
                           @JsonProperty("storageType") String storageType,
                           @JsonProperty("architecture") String architecture,
                           @JsonProperty("clusterType") String clusterType) {
        this.count = count == null ? 1 : (int) requireNonNegative("count", count);
        this.vcpu = vcpu == null ? OptionalDouble.empty() : OptionalDouble.of(requireNonNegative("vcpu", vcpu));
        this.memoryGb = memoryGb == null ? OptionalDouble.empty() : OptionalDouble.of(requireNonNegative("memoryGb", memoryGb));
        this.diskGb = diskGb == null ? OptionalDouble.empty() : OptionalDouble.of(requireNonNegative("diskGb", diskGb));
        this.bandwidthGbps = bandwidthGbps == null ? OptionalDouble.empty() : OptionalDouble.of(bandwidthGbps);
        this.diskSpeed = validateEnum("diskSpeed", validDiskSpeeds, diskSpeed == null ? "fast" : diskSpeed);
        this.storageType = validateEnum("storageType", validStorageTypes, storageType == null ? "any" : storageType);
        this.architecture = validateEnum("architecture", validArchitectures, architecture == null ? "x86_64" : architecture);
        this.clusterType = clusterType == null ? null : validateEnum("clusterType", validClusterTypes, clusterType);
    }

    /** Returns a new ClusterCapacity equal to {@code this}, but with the given count. */
    public ClusterCapacity withCount(int count) {
        return new ClusterCapacity(count, vcpuOrNull(), memoryGbOrNull(), diskGbOrNull(), bandwidthGbpsOrNull(),
                                   diskSpeed, storageType, architecture, clusterType);
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
    @JsonGetter("diskSpeed") public String diskSpeed() { return diskSpeed; }
    @JsonGetter("storageType") public String storageType() { return storageType; }
    @JsonGetter("architecture") public String architecture() { return architecture; }
    @JsonGetter("clusterType") public String clusterType() { return clusterType; }

    @JsonIgnore public Double vcpu() { return vcpu.orElse(0.0); }
    @JsonIgnore public Double memoryGb() { return memoryGb.orElse(0.0); }
    @JsonIgnore public Double diskGb() { return diskGb.orElse(0.0); }
    @JsonIgnore public double bandwidthGbps() { return bandwidthGbps.orElse(1.0); }

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
                ", clusterType=" + clusterType +
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
                architecture.equals(that.architecture) &&
                clusterType.equals(that.clusterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, clusterType);
    }

}
