// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The advertised node resources of a host, similar to config-provision's NodeResources,
 * but with additional host-specific resources like the number of containers.
 *
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostResources {
    private static final Set<String> validDiskSpeeds = Set.of("slow", "fast");
    private static final Set<String> validStorageTypes = Set.of("remote", "local");
    private static final Set<String> validClusterTypes = Set.of("container", "content", "combined", "admin");
    private static final Set<String> validArchitectures = Set.of("arm64", "x86_64");

    private final double vcpu;
    private final double memoryGb;
    private final double diskGb;
    private final double bandwidthGbps;

    private final String diskSpeed;
    private final String storageType;

    private final Optional<String> clusterType;

    private final int containers;
    private final String architecture;

    @JsonCreator
    public HostResources(@JsonProperty("vcpu") Double vcpu,
                         @JsonProperty("memoryGb") Double memoryGb,
                         @JsonProperty("diskGb") Double diskGb,
                         @JsonProperty("bandwidthGbps") Double bandwidthGbps,
                         @JsonProperty("diskSpeed") String diskSpeed,
                         @JsonProperty("storageType") String storageType,
                         @JsonProperty("clusterType") String clusterType,
                         @JsonProperty("containers") Integer containers,
                         @JsonProperty("architecture") String architecture) {
        this.vcpu = requirePositive("vcpu", vcpu);
        this.memoryGb = requirePositive("memoryGb", memoryGb);
        this.diskGb = requirePositive("diskGb", diskGb);
        this.bandwidthGbps = requirePositive("bandwidthGbps", bandwidthGbps);
        this.diskSpeed = validateEnum("diskSpeed", validDiskSpeeds, diskSpeed);
        this.storageType = validateEnum("storageType", validStorageTypes, storageType);
        this.clusterType = Optional.ofNullable(clusterType).map(cType -> validateEnum("clusterType", validClusterTypes, cType));
        this.containers = requirePositive("containers", containers);
        this.architecture = validateEnum("architecture", validArchitectures, architecture);
    }

    @JsonProperty("vcpu")
    public double vcpu() { return vcpu; }

    @JsonProperty("memoryGb")
    public double memoryGb() { return memoryGb; }

    @JsonProperty("diskGb")
    public double diskGb() { return diskGb; }

    @JsonProperty("bandwidthGbps")
    public double bandwidthGbps() { return bandwidthGbps; }

    @JsonProperty("diskSpeed")
    public String diskSpeed() { return diskSpeed; }

    @JsonProperty("storageType")
    public String storageType() { return storageType; }

    @JsonProperty("clusterType")
    public String clusterTypeOrNull() { return clusterType.orElse(null); }

    @JsonIgnore
    public Optional<String> clusterType() { return clusterType; }

    @JsonProperty("containers")
    public int containers() { return containers; }

    @JsonProperty("architecture")
    public String architecture() { return architecture; }

    public boolean satisfiesClusterType(String clusterType) {
        return this.clusterType.map(clusterType::equalsIgnoreCase).orElse(true);
    }

    private static double requirePositive(String name, Double value) {
        requireNonNull(name, value);
        if (value <= 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }

    private static int requirePositive(String name, Integer value) {
        requireNonNull(name, value);
        if (value <= 0)
            throw new IllegalArgumentException("'" + name + "' must be positive, was " + value);
        return value;
    }

    private static String validateEnum(String name, Set<String> validValues, String value) {
        requireNonNull(name, value);
        if (!validValues.contains(value))
            throw new IllegalArgumentException("Invalid " + name + ", valid values are: " +
                    validValues + ", got: " + value);
        return value;
    }

    private static <T> T requireNonNull(String name, T value) {
        return Objects.requireNonNull(value, () -> "'" + name + "' has not been specified");
    }

    @Override
    public String toString() {
        return "HostResources{" +
                "vcpu=" + vcpu +
                ", memoryGb=" + memoryGb +
                ", diskGb=" + diskGb +
                ", bandwidthGbps=" + bandwidthGbps +
                ", diskSpeed='" + diskSpeed + '\'' +
                ", storageType='" + storageType + '\'' +
                ", clusterType='" + clusterType + '\'' +
                ", containers=" + containers +
                ", architecture=" + architecture +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostResources resources = (HostResources) o;
        return Double.compare(resources.vcpu, vcpu) == 0 &&
                Double.compare(resources.memoryGb, memoryGb) == 0 &&
                Double.compare(resources.diskGb, diskGb) == 0 &&
                Double.compare(resources.bandwidthGbps, bandwidthGbps) == 0 &&
                diskSpeed.equals(resources.diskSpeed) &&
                storageType.equals(resources.storageType) &&
                clusterType.equals(resources.clusterType) &&
                containers == resources.containers &&
                architecture.equals(resources.architecture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType, clusterType, containers, architecture);
    }
}
