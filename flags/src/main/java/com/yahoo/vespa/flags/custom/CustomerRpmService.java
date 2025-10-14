// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
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

    /**
     * Optional list of additional yum repositories to enable for this RPM.
     */
    @JsonProperty("repositories")
    private final List<String> repositories;

    /**
     * Optional disabled tag for the service unit. Defaults to false.
     * Enables removal of service without persistent storage on the host.
     */
    @JsonProperty("disabled")
    private final Boolean disabled;

    @JsonCreator
    public CustomerRpmService(
        @JsonProperty(value = "unit") String unit,
        @JsonProperty(value = "package") String packageName,
        @JsonProperty(value = "memory") Double memoryLimitMib,
        @JsonProperty("cpu") Double cpuLimitCores,
        @JsonProperty("repositories") List<String> repositories,
        @JsonProperty("disabled") Boolean disabled
    ) {
        this.unit = Objects.requireNonNull(unit);
        this.packageName = packageName;
        this.memoryLimitMib = Objects.requireNonNull(memoryLimitMib);
        this.cpuLimitCores = cpuLimitCores == null || cpuLimitCores <= 0.0 ? null : cpuLimitCores;
        this.repositories = repositories == null ? List.of() : repositories;
        this.disabled = disabled != null && disabled;
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

    public boolean disabled() {
        return disabled;
    }

    public Optional<Double> cpuLimitCores() {
        return Optional.ofNullable(cpuLimitCores);
    }

    public List<String> repositories() {
        return repositories;
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
            repositories().equals(other.repositories()) &&
            cpuLimitCores().equals(other.cpuLimitCores());
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitName(), packageName(), memoryLimitMib(), cpuLimitCores(), repositories(), disabled());
    }

    @Override
    public String toString() {
        return "{ unit: %s, package: %s, memory: %s MiB, cpu: %s, repositories: %s, disabled: %s }"
                .formatted(
                        unitName(), packageName(), memoryLimitMib(),
                        cpuLimitCores().map(Object::toString).orElse("unlimited"),
                        String.join(", ", repositories()),
                        disabled()
                );
    }

}
