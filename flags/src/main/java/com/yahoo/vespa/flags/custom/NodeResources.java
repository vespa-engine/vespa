// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeResources {
    private static final Set<String> validDiskSpeeds = Set.of("any", "slow", "fast");

    @JsonProperty("vcpu")
    private final double vcpu;

    @JsonProperty("memoryGb")
    private final double memoryGb;

    @JsonProperty("diskGb")
    private final double diskGb;

    @JsonProperty("bandwidthGbps")
    private final double bandwidthGbps;

    @JsonProperty("diskSpeed")
    private final String diskSpeed;

    public NodeResources(@JsonProperty("vcpu") double vcpu,
                         @JsonProperty("memoryGb") double memoryGb,
                         @JsonProperty("diskGb") double diskGb,
                         @JsonProperty("bandwidthGbps") Double bandwidthGbps,
                         @JsonProperty("diskSpeed") String diskSpeed) {
        this.vcpu = requirePositive("vcpu", vcpu);
        this.memoryGb = requirePositive("memoryGb", memoryGb);
        this.diskGb = requirePositive("diskGb", diskGb);
        this.bandwidthGbps = requirePositive("bandwidthGbps", Optional.ofNullable(bandwidthGbps).orElse(0.3));
        this.diskSpeed = Optional.ofNullable(diskSpeed).orElse("fast");

        if (!validDiskSpeeds.contains(this.diskSpeed))
            throw new IllegalArgumentException("Invalid diskSpeed, valid values are: " + validDiskSpeeds + ", got: " + diskSpeed);
    }

    public double vcpu() {
        return vcpu;
    }

    public double memoryGb() {
        return memoryGb;
    }

    public double diskGb() {
        return diskGb;
    }

    public double bandwidthGbps() {
        return bandwidthGbps;
    }

    public String diskSpeed() {
        return diskSpeed;
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
        NodeResources resources = (NodeResources) o;
        return Double.compare(resources.vcpu, vcpu) == 0 &&
                Double.compare(resources.memoryGb, memoryGb) == 0 &&
                Double.compare(resources.diskGb, diskGb) == 0 &&
                Double.compare(resources.bandwidthGbps, bandwidthGbps) == 0 &&
                diskSpeed.equals(resources.diskSpeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed);
    }
}
