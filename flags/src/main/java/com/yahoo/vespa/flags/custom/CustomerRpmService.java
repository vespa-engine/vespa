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
     * The identifier or name of the service unit, typically used in systemd,
     * for instance {@code my-service.service}.
     */
    @JsonProperty(value = "unit", required = true)
    private final String unit;

    /**
     * The download URL of the RPM package associated with the service unit.
     */
    @JsonProperty(value = "url", required = true)
    private final String url;

    /**
     * Memory limit in mebibytes (MiB) for the service unit.
     * This limit will be enforced by the host operating system.
     */
    @JsonProperty(value = "memory", required = true)
    private final double memoryLimitMib;

    /**
     * Optional CPU limit for the service unit in fraction of cores, e.g
     * 0.5 is 50% of one core, 2.0 is 100% of 2 cores. This limit will
     * be enforced by the host operating system. Null indicates no hard limit.
     */
    @JsonProperty("cpu")
    private final Double cpuLimitCores;

    @JsonCreator
    public CustomerRpmService(
        @JsonProperty(value = "unit", required = true) String unit,
        @JsonProperty(value = "url", required = true) String url,
        @JsonProperty(value = "memory", required = true) double memoryLimitMib,
        @JsonProperty("cpu") Double cpuLimitCores
    ) {
        this.unit = Objects.requireNonNull(unit);
        this.url = Objects.requireNonNull(url);
        this.memoryLimitMib = memoryLimitMib;
        this.cpuLimitCores = cpuLimitCores == null || cpuLimitCores <= 0.0 ? null : cpuLimitCores;
    }

    public String unitName() {
        return unit;
    }

    public String url() {
        return url;
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
            url.equals(other.url) &&
            memoryLimitMib == other.memoryLimitMib &&
            cpuLimitCores().equals(other.cpuLimitCores());
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, url, memoryLimitMib, cpuLimitCores);
    }

}
