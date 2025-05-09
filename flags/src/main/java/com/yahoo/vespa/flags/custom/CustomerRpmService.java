// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

/***
 * Represents a single customer RPM service running on a host.
 *
 * @author Sigve Røkenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class CustomerRpmService {

    /**
     * The identifier or name of the systemd service unit
     */
    @JsonProperty(value = "unit")
    private final String unit;

    /**
     * Optional override for yum package name in cases where
     * the package name and service unit name are different
     */
    @JsonProperty(value = "package")
    private final String packageName;

    /**
     * Memory limit in mebibytes (MiB) for the service unit.
     * This limit will be enforced by the host operating system.
     */
    @JsonProperty(value = "memory")
    private final Double memoryLimitMib;

    /**
     * Optional CPU limit for the service unit in fraction of cores, e.g
     * 0.5 is 50% of one core, 2.0 is 100% of 2 cores. This limit will
     * be enforced by the host operating system. Null indicates no hard limit.
     */
    @JsonProperty("cpu")
    private final Double cpuLimitCores;

    @JsonCreator
    public CustomerRpmService(
        @JsonProperty(value = "unit") String unit,
        @JsonProperty(value = "package") String packageName,
        @JsonProperty(value = "memory") Double memoryLimitMib,
        @JsonProperty("cpu") Double cpuLimitCores
    ) {
        this.unit = Objects.requireNonNull(unit);
        this.packageName = packageName;
        this.memoryLimitMib = Objects.requireNonNull(memoryLimitMib);
        this.cpuLimitCores = cpuLimitCores == null || cpuLimitCores <= 0.0 ? null : cpuLimitCores;
    }

    public String unitName() {
        return unit;
    }

    public String packageName() {
        return packageName == null ? unitName() : packageName;
    }

    public double memoryLimitMib() {
        return memoryLimitMib;
    }

    public Optional<Double> cpuLimitCores() {
        return Optional.ofNullable(cpuLimitCores);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerRpmService other = (CustomerRpmService) o;
        return
            unit.equals(other.unit) &&
            memoryLimitMib.equals(other.memoryLimitMib) &&
            packageName().equals(other.packageName()) &&
            cpuLimitCores().equals(other.cpuLimitCores());
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitName(), packageName(), memoryLimitMib(), cpuLimitCores());
    }

    @Override
    public String toString() {
        return "{ unit: %s, package: %s, memory: %s MiB, cpu: %s }"
                .formatted(unitName(), packageName(), memoryLimitMib(),
                        cpuLimitCores().map(Object::toString).orElse("unlimited"));
    }

}
